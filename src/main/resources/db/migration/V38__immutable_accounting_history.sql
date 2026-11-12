-- Operational cuotas retain mutable payment status and timbre configuration.
-- Only this journal is the immutable accounting record.
CREATE TABLE historial_contable (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    socio_id BIGINT NOT NULL,
    entidad VARCHAR(20) NOT NULL,
    entidad_id BIGINT NOT NULL,
    tipo VARCHAR(20) NOT NULL CHECK (tipo IN ('EMISION', 'ANULACION')),
    monto NUMERIC(12,2) NOT NULL,
    registrado_en TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    autor TEXT NOT NULL,
    motivo TEXT NOT NULL,
    original_id BIGINT REFERENCES historial_contable(id),
    datos JSONB NOT NULL,
    UNIQUE (entidad, entidad_id, tipo)
);
CREATE INDEX historial_contable_socio ON historial_contable(socio_id, id);

INSERT INTO historial_contable(socio_id, entidad, entidad_id, tipo, monto, autor, motivo, datos)
SELECT socio_id, 'CUOTA', id, 'EMISION', monto, 'MIGRACION', 'Datos existentes; autor y fecha de registro originales desconocidos', to_jsonb(c) FROM cuotas c
UNION ALL
SELECT socio_id, 'PAGO', id, 'EMISION', -monto_cobrado, 'MIGRACION', 'Datos existentes; autor y fecha de registro originales desconocidos', to_jsonb(p) FROM pagos p
UNION ALL
SELECT socio_id, 'REINTEGRO', id, 'EMISION', monto, 'MIGRACION', 'Datos existentes; autor y fecha de registro originales desconocidos', to_jsonb(r) FROM reintegros r;

INSERT INTO historial_contable(socio_id, entidad, entidad_id, tipo, monto, autor, motivo, original_id, datos)
SELECT h.socio_id, h.entidad, h.entidad_id, 'ANULACION', -h.monto, 'MIGRACION',
       'Cuota anulada antes de la migración; se conserva el original', h.id, h.datos
FROM historial_contable h
WHERE (h.entidad = 'CUOTA' AND EXISTS (SELECT 1 FROM cuotas c WHERE c.id = h.entidad_id AND c.estado = 'ANULADA'))
   OR (h.entidad IN ('PAGO', 'REINTEGRO') AND EXISTS
       (SELECT 1 FROM cuotas c WHERE c.id = (h.datos->>'cuota_id')::BIGINT AND c.estado = 'ANULADA'));

CREATE FUNCTION impedir_mutacion_historial_contable() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'El historial contable es inmutable; registre una anulación con motivo';
END;
$$;
CREATE TRIGGER historial_contable_inmutable
BEFORE UPDATE OR DELETE OR TRUNCATE ON historial_contable
FOR EACH STATEMENT EXECUTE FUNCTION impedir_mutacion_historial_contable();
REVOKE UPDATE, DELETE, TRUNCATE ON historial_contable FROM PUBLIC;

-- Runtime roles need SELECT on the journal, never direct INSERT. The trusted
-- migration owner writes through this function with a pinned schema search path.
CREATE FUNCTION registrar_historial_contable() RETURNS trigger LANGUAGE plpgsql
SECURITY DEFINER SET search_path FROM CURRENT AS $$
DECLARE
    v_entidad TEXT := CASE TG_TABLE_NAME WHEN 'cuotas' THEN 'CUOTA' WHEN 'pagos' THEN 'PAGO' ELSE 'REINTEGRO' END;
    importe NUMERIC;
    v_autor TEXT := COALESCE(NULLIF(current_setting('app.contable_autor', true), ''), 'SISTEMA:SQL');
    v_motivo TEXT := COALESCE(NULLIF(current_setting('app.contable_motivo', true), ''), 'Operación SQL sin contexto de aplicación');
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION 'No se permite borrar movimientos contables';
    END IF;
    IF TG_OP = 'UPDATE' THEN
        -- Status/allocation and recurring timbre metadata are not new charges.
        IF (to_jsonb(NEW) - ARRAY['estado','monto_timbre','timbre_recurrente']) IS DISTINCT FROM
           (to_jsonb(OLD) - ARRAY['estado','monto_timbre','timbre_recurrente']) THEN
            RAISE EXCEPTION 'No se permite modificar movimientos emitidos';
        END IF;
        IF TG_TABLE_NAME = 'cuotas' THEN
            IF OLD.estado = 'ANULADA' AND NEW.estado <> 'ANULADA' THEN
                RAISE EXCEPTION 'Una cuota anulada no puede reactivarse';
            END IF;
            IF NEW.estado = 'ANULADA' AND OLD.estado <> 'ANULADA' THEN
                IF NULLIF(btrim(current_setting('app.contable_motivo', true)), '') IS NULL THEN
                    RAISE EXCEPTION 'La anulación requiere motivo';
                END IF;
                IF EXISTS (SELECT 1 FROM pagos WHERE socio_id = NEW.socio_id)
                   OR EXISTS (SELECT 1 FROM reintegros WHERE cuota_id = NEW.id) THEN
                    RAISE EXCEPTION 'La cuota tiene pagos o reintegros relacionados; requiere conciliación';
                END IF;
                INSERT INTO historial_contable(socio_id, entidad, entidad_id, tipo, monto, autor, motivo, original_id, datos)
                SELECT NEW.socio_id, v_entidad, NEW.id, 'ANULACION', -h.monto, v_autor, v_motivo, h.id, to_jsonb(NEW)
                FROM historial_contable h WHERE h.entidad = v_entidad AND h.entidad_id = NEW.id AND h.tipo = 'EMISION';
            END IF;
        END IF;
        RETURN NEW;
    END IF;
    importe := CASE WHEN TG_TABLE_NAME = 'pagos' THEN -(to_jsonb(NEW)->>'monto_cobrado')::NUMERIC
                    ELSE (to_jsonb(NEW)->>'monto')::NUMERIC END;
    INSERT INTO historial_contable(socio_id, entidad, entidad_id, tipo, monto, autor, motivo, datos)
    VALUES (NEW.socio_id, v_entidad, NEW.id, 'EMISION', importe, v_autor, v_motivo, to_jsonb(NEW));
    IF TG_TABLE_NAME = 'cuotas' THEN
        IF NEW.estado = 'ANULADA' THEN
            RAISE EXCEPTION 'Emita la cuota antes de anularla';
        END IF;
    ELSIF EXISTS (SELECT 1 FROM cuotas WHERE id = (to_jsonb(NEW)->>'cuota_id')::BIGINT AND estado = 'ANULADA') THEN
        RAISE EXCEPTION 'No se permite imputar a una cuota anulada';
    END IF;
    RETURN NEW;
END;
$$;
REVOKE ALL ON FUNCTION registrar_historial_contable() FROM PUBLIC;
CREATE TRIGGER cuotas_historial BEFORE INSERT OR UPDATE OR DELETE ON cuotas
FOR EACH ROW EXECUTE FUNCTION registrar_historial_contable();
CREATE TRIGGER pagos_historial BEFORE INSERT OR UPDATE OR DELETE ON pagos
FOR EACH ROW EXECUTE FUNCTION registrar_historial_contable();
CREATE TRIGGER reintegros_historial BEFORE INSERT OR UPDATE OR DELETE ON reintegros
FOR EACH ROW EXECUTE FUNCTION registrar_historial_contable();
CREATE TRIGGER cuotas_no_truncate BEFORE TRUNCATE ON cuotas
FOR EACH STATEMENT EXECUTE FUNCTION impedir_mutacion_historial_contable();
CREATE TRIGGER pagos_no_truncate BEFORE TRUNCATE ON pagos
FOR EACH STATEMENT EXECUTE FUNCTION impedir_mutacion_historial_contable();
CREATE TRIGGER reintegros_no_truncate BEFORE TRUNCATE ON reintegros
FOR EACH STATEMENT EXECUTE FUNCTION impedir_mutacion_historial_contable();

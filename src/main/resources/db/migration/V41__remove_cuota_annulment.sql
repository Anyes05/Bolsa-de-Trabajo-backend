-- Se quita la anulación de cuotas: el historial contable solo registra emisiones.
-- Corta si quedaran datos de anulaciones, para no perder información en silencio.
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM cuotas WHERE estado = 'ANULADA')
       OR EXISTS (SELECT 1 FROM historial_contable WHERE tipo <> 'EMISION' OR original_id IS NOT NULL) THEN
        RAISE EXCEPTION 'Hay cuotas anuladas o asientos de anulación: revisarlos antes de aplicar V41';
    END IF;
END;
$$;

ALTER TABLE cuotas DROP CONSTRAINT cuotas_estado_check;
ALTER TABLE cuotas ADD CONSTRAINT cuotas_estado_check CHECK (estado IN ('PENDIENTE', 'PAGADO'));

ALTER TABLE historial_contable DROP COLUMN original_id;
ALTER TABLE historial_contable DROP CONSTRAINT historial_contable_tipo_check;
ALTER TABLE historial_contable ADD CONSTRAINT historial_contable_tipo_check CHECK (tipo = 'EMISION');

CREATE OR REPLACE FUNCTION impedir_mutacion_historial_contable() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'El historial contable es inmutable';
END;
$$;

CREATE OR REPLACE FUNCTION registrar_historial_contable() RETURNS trigger LANGUAGE plpgsql
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
        -- El estado de pago y la configuración del timbre recurrente no son cargos nuevos.
        IF (to_jsonb(NEW) - ARRAY['estado','monto_timbre','timbre_recurrente']) IS DISTINCT FROM
           (to_jsonb(OLD) - ARRAY['estado','monto_timbre','timbre_recurrente']) THEN
            RAISE EXCEPTION 'No se permite modificar movimientos emitidos';
        END IF;
        RETURN NEW;
    END IF;
    importe := CASE WHEN TG_TABLE_NAME = 'pagos' THEN -(to_jsonb(NEW)->>'monto_cobrado')::NUMERIC
                    ELSE (to_jsonb(NEW)->>'monto')::NUMERIC END;
    INSERT INTO historial_contable(socio_id, entidad, entidad_id, tipo, monto, autor, motivo, datos)
    VALUES (NEW.socio_id, v_entidad, NEW.id, 'EMISION', importe, v_autor, v_motivo, to_jsonb(NEW));
    RETURN NEW;
END;
$$;
REVOKE ALL ON FUNCTION registrar_historial_contable() FROM PUBLIC;

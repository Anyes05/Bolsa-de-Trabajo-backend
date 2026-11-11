ALTER TABLE socios
    ADD COLUMN IF NOT EXISTS campos_ficticios TEXT,
    ADD COLUMN IF NOT EXISTS importado_masivo BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS acceso_habilitado BOOLEAN NOT NULL DEFAULT TRUE;

INSERT INTO rubros_empleo (nombre_rubro, activo)
SELECT 'Sin clasificar', TRUE
WHERE NOT EXISTS (
    SELECT 1 FROM rubros_empleo WHERE lower(nombre_rubro) = lower('Sin clasificar')
);

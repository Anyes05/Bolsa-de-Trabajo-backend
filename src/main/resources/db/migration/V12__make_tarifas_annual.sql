ALTER TABLE tarifas
    ADD COLUMN IF NOT EXISTS anio INTEGER,
    ADD COLUMN IF NOT EXISTS dia_vencimiento SMALLINT;

UPDATE tarifas
SET anio = EXTRACT(YEAR FROM periodo),
    dia_vencimiento = EXTRACT(DAY FROM fecha_vencimiento)
WHERE anio IS NULL OR dia_vencimiento IS NULL;

ALTER TABLE tarifas
    ALTER COLUMN anio SET NOT NULL,
    ALTER COLUMN dia_vencimiento SET NOT NULL,
    ADD CONSTRAINT tarifas_anio_check CHECK (anio BETWEEN 2000 AND 9999),
    ADD CONSTRAINT tarifas_dia_vencimiento_check CHECK (dia_vencimiento BETWEEN 1 AND 31);

ALTER TABLE tarifas
    DROP CONSTRAINT IF EXISTS tarifas_periodo_key,
    ADD CONSTRAINT tarifas_anio_key UNIQUE (anio),
    DROP COLUMN periodo,
    DROP COLUMN fecha_vencimiento;
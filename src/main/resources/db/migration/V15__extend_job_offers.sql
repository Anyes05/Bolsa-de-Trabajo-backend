ALTER TABLE ofertas_empleo
    ADD COLUMN IF NOT EXISTS zona VARCHAR(120),
    ADD COLUMN IF NOT EXISTS salario VARCHAR(120),
    ADD COLUMN IF NOT EXISTS requisitos TEXT;

CREATE INDEX IF NOT EXISTS ofertas_empleo_socio_id_idx ON ofertas_empleo(socio_id);
CREATE INDEX IF NOT EXISTS ofertas_empleo_fecha_cierre_idx ON ofertas_empleo(fecha_cierre)
    WHERE estado IN ('ACTIVA', 'PAUSADA');
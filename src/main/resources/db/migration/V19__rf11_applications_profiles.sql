ALTER TABLE perfiles_laborales
    ADD COLUMN IF NOT EXISTS libreta VARCHAR(120);

ALTER TABLE postulaciones
    ADD COLUMN IF NOT EXISTS perfil_laboral_id BIGINT REFERENCES perfiles_laborales(id),
    ADD COLUMN IF NOT EXISTS cv_id BIGINT REFERENCES cvs(id),
    ADD COLUMN IF NOT EXISTS estado VARCHAR(20) NOT NULL DEFAULT 'RECIBIDA'
        CHECK (estado IN ('RECIBIDA', 'REVISADA', 'CONTACTADA', 'SELECCIONADA', 'DESCARTADA'));

CREATE INDEX IF NOT EXISTS postulaciones_perfil_laboral_id_idx ON postulaciones(perfil_laboral_id);
CREATE INDEX IF NOT EXISTS postulaciones_cv_id_idx ON postulaciones(cv_id);
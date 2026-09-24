ALTER TABLE cvs
    ADD COLUMN IF NOT EXISTS perfil_laboral_id BIGINT REFERENCES perfiles_laborales(id);

ALTER TABLE perfiles_laborales
    ADD COLUMN IF NOT EXISTS visible BOOLEAN NOT NULL DEFAULT TRUE;

UPDATE cvs cv
SET perfil_laboral_id = (
    SELECT pl.id
    FROM perfiles_laborales pl
    WHERE pl.postulante_id = cv.postulante_id
    ORDER BY pl.id
    LIMIT 1
)
WHERE cv.perfil_laboral_id IS NULL;

CREATE INDEX IF NOT EXISTS cvs_perfil_laboral_id_idx ON cvs(perfil_laboral_id);
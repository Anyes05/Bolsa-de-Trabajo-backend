DROP INDEX IF EXISTS cvs_un_activo_por_postulante;

CREATE UNIQUE INDEX IF NOT EXISTS cvs_un_activo_por_perfil
    ON cvs (postulante_id, perfil_laboral_id)
    WHERE activo = TRUE;
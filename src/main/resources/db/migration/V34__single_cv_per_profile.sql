CREATE TABLE cv_archivos_pendientes_borrado (
    ruta VARCHAR(500) PRIMARY KEY,
    creado_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

UPDATE cvs cv SET perfil_laboral_id = (
    SELECT id FROM perfiles_laborales WHERE postulante_id = cv.postulante_id ORDER BY id LIMIT 1
) WHERE perfil_laboral_id IS NULL;

CREATE TEMP TABLE cv_consolidacion ON COMMIT DROP AS
SELECT id,
       first_value(id) OVER (
           PARTITION BY perfil_laboral_id ORDER BY activo DESC, fecha_carga DESC, id DESC
       ) AS conservar
FROM cvs WHERE perfil_laboral_id IS NOT NULL;

UPDATE postulaciones a SET cv_id = c.conservar
FROM cv_consolidacion c WHERE a.cv_id = c.id AND c.id <> c.conservar;

INSERT INTO cv_archivos_pendientes_borrado (ruta)
SELECT DISTINCT cv.ruta_archivo_cv FROM cvs cv
JOIN cv_consolidacion c ON c.id = cv.id AND c.id <> c.conservar
WHERE cv.ruta_archivo_cv IS NOT NULL
ON CONFLICT DO NOTHING;

DELETE FROM cvs WHERE id IN (SELECT id FROM cv_consolidacion WHERE id <> conservar);

-- CVs heredados sin perfil no pueden utilizarse ni mantenerse como un segundo CV.
INSERT INTO cv_archivos_pendientes_borrado (ruta)
SELECT DISTINCT ruta_archivo_cv FROM cvs WHERE perfil_laboral_id IS NULL AND ruta_archivo_cv IS NOT NULL
ON CONFLICT DO NOTHING;
UPDATE postulaciones SET cv_id = NULL WHERE cv_id IN (SELECT id FROM cvs WHERE perfil_laboral_id IS NULL);
DELETE FROM cvs WHERE perfil_laboral_id IS NULL;

UPDATE cvs SET activo = TRUE, version = 1;
ALTER TABLE cvs ALTER COLUMN perfil_laboral_id SET NOT NULL;
DROP INDEX IF EXISTS cvs_un_activo_por_perfil;
ALTER TABLE cvs ADD CONSTRAINT cvs_unico_por_perfil UNIQUE (perfil_laboral_id);
ALTER TABLE cvs ADD CONSTRAINT cvs_siempre_disponible CHECK (activo = TRUE AND version = 1);
ALTER TABLE postulaciones DROP CONSTRAINT IF EXISTS postulaciones_cv_id_fkey;
ALTER TABLE postulaciones ADD CONSTRAINT postulaciones_cv_id_fkey
    FOREIGN KEY (cv_id) REFERENCES cvs(id) ON DELETE SET NULL;

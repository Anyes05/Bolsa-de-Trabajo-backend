\set ON_ERROR_STOP on
BEGIN;
SET LOCAL search_path = public, pg_catalog;
SET LOCAL lock_timeout = '10s';
SET LOCAL statement_timeout = '5min';
SELECT set_config('reset.expected_database', :'database', true) AS reset_database \gset
DO $$
BEGIN
 IF current_database() <> current_setting('reset.expected_database') THEN
   RAISE EXCEPTION 'Database identity mismatch';
 END IF;
 IF NOT EXISTS (SELECT 1 FROM usuarios WHERE rol = 'ADMIN') THEN
   RAISE EXCEPTION 'At least one existing ADMIN required';
 END IF;
 IF (SELECT count(*) FROM flyway_schema_history WHERE success) <> 40
    OR EXISTS (SELECT 1 FROM flyway_schema_history WHERE NOT success)
    OR EXISTS (SELECT version FROM flyway_schema_history
       EXCEPT SELECT generate_series(1,40)::text) THEN
   RAISE EXCEPTION 'Expected successful migrations V1-V40; review reset for schema changes';
 END IF;
 IF EXISTS (
   SELECT FROM pg_tables WHERE schemaname = 'public' AND tablename <> ALL(ARRAY[
    'usuarios','rubros_empleo','flyway_schema_history','direcciones','socios',
    'cuotas','pagos','ofertas_empleo','postulaciones','cvs','experiencias_laborales',
    'formaciones_academicas','idiomas','cv_idiomas','habilidades_estandar','cv_habilidades',
    'comunicados','perfiles_laborales','perfil_rubros','postulantes','tarifas',
    'reintegros_programados','reintegros','notificaciones','cv_archivos_pendientes_borrado',
    'socio_historial_suscripcion','historial_contable'])
 ) THEN RAISE EXCEPTION 'Unknown public table; review reset'; END IF;
END $$;
LOCK TABLE usuarios, rubros_empleo, flyway_schema_history, direcciones, socios,
 cuotas, pagos, ofertas_empleo, postulaciones, cvs, experiencias_laborales,
 formaciones_academicas, idiomas, cv_idiomas, habilidades_estandar, cv_habilidades,
 comunicados, perfiles_laborales, perfil_rubros, postulantes, tarifas,
 reintegros_programados, reintegros, notificaciones, cv_archivos_pendientes_borrado,
 socio_historial_suscripcion, historial_contable IN ACCESS EXCLUSIVE MODE;
CREATE TEMP TABLE preserved_admin AS SELECT * FROM usuarios WHERE rol = 'ADMIN';
CREATE TEMP TABLE preserved_rubros AS SELECT * FROM rubros_empleo;
CREATE TEMP TABLE preserved_flyway AS SELECT * FROM flyway_schema_history;
CREATE TEMP TABLE reset_keys AS
 SELECT ruta_archivo_cv AS key FROM cvs WHERE ruta_archivo_cv IS NOT NULL
 UNION SELECT ruta FROM cv_archivos_pendientes_borrado;
DO $$
BEGIN
 IF EXISTS (SELECT FROM reset_keys WHERE key !~ '^postulantes/[0-9]+/cvs/[^/]+$') THEN
   RAISE EXCEPTION 'Unexpected storage key; manual review required';
 END IF;
 IF EXISTS (SELECT FROM pg_trigger WHERE tgrelid IN
 ('historial_contable'::regclass,'cuotas'::regclass,'pagos'::regclass,'reintegros'::regclass)
 AND NOT tgisinternal AND tgenabled <> 'O') THEN
   RAISE EXCEPTION 'Accounting protection is not enabled';
 END IF;
 IF (SELECT count(*) FROM pg_trigger WHERE NOT tgisinternal AND
   (tgrelid, tgname) IN (
    ('historial_contable'::regclass,'historial_contable_inmutable'),
    ('cuotas'::regclass,'cuotas_historial'),('pagos'::regclass,'pagos_historial'),
    ('reintegros'::regclass,'reintegros_historial'),
    ('cuotas'::regclass,'cuotas_no_truncate'),('pagos'::regclass,'pagos_no_truncate'),
    ('reintegros'::regclass,'reintegros_no_truncate'))) <> 7 THEN
   RAISE EXCEPTION 'Accounting protection missing';
 END IF;
END $$;
ALTER TABLE historial_contable DISABLE TRIGGER historial_contable_inmutable;
ALTER TABLE cuotas DISABLE TRIGGER cuotas_no_truncate;
ALTER TABLE pagos DISABLE TRIGGER pagos_no_truncate;
ALTER TABLE reintegros DISABLE TRIGGER reintegros_no_truncate;
TRUNCATE historial_contable, cuotas, pagos, reintegros, reintegros_programados,
 socio_historial_suscripcion, notificaciones, comunicados, postulaciones, ofertas_empleo,
 cv_habilidades, cv_idiomas, experiencias_laborales, formaciones_academicas,
 cvs, cv_archivos_pendientes_borrado, perfil_rubros, perfiles_laborales, postulantes,
 socios, direcciones, tarifas, idiomas, habilidades_estandar;
ALTER TABLE historial_contable ENABLE TRIGGER historial_contable_inmutable;
ALTER TABLE cuotas ENABLE TRIGGER cuotas_no_truncate;
ALTER TABLE pagos ENABLE TRIGGER pagos_no_truncate;
ALTER TABLE reintegros ENABLE TRIGGER reintegros_no_truncate;
DELETE FROM usuarios WHERE rol <> 'ADMIN';
DO $$
BEGIN
 IF EXISTS ((SELECT * FROM preserved_admin EXCEPT SELECT * FROM usuarios)
 UNION ALL (SELECT * FROM usuarios EXCEPT SELECT * FROM preserved_admin))
 OR EXISTS ((SELECT * FROM preserved_rubros EXCEPT SELECT * FROM rubros_empleo)
 UNION ALL (SELECT * FROM rubros_empleo EXCEPT SELECT * FROM preserved_rubros))
 OR EXISTS ((SELECT * FROM preserved_flyway EXCEPT SELECT * FROM flyway_schema_history)
 UNION ALL (SELECT * FROM flyway_schema_history EXCEPT SELECT * FROM preserved_flyway))
 THEN RAISE EXCEPTION 'Preserved data changed'; END IF;
END $$;
SELECT json_build_object('format',1,'database',current_database(),'committed', :'apply'::boolean,
 'keys',COALESCE((SELECT json_agg(key ORDER BY key) FROM reset_keys),'[]'::json));
\if :apply
COMMIT;
\else
ROLLBACK;
\endif

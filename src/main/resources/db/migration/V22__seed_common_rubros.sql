-- Rubros laborales adicionales (catálogo compartido: socios, ofertas y postulantes)
INSERT INTO rubros_empleo (nombre_rubro) VALUES
    ('Atencion al Cliente'),
    ('Marketing'),
    ('Recursos Humanos'),
    ('Limpieza'),
    ('Seguridad'),
    ('Gastronomia'),
    ('Salud'),
    ('Educacion'),
    ('Electricidad'),
    ('Plomeria'),
    ('Diseno Grafico'),
    ('Turismo'),
    ('Agricultura'),
    ('Produccion'),
    ('Call Center'),
    ('Almacen'),
    ('Comercio'),
    ('Camara empresarial')
ON CONFLICT (nombre_rubro) DO NOTHING;

-- Socios demo: asignar rubro de catálogo (antes solo tenían texto en giro)
UPDATE socios s
SET rubro_id = r.id,
    giro = r.nombre_rubro
FROM rubros_empleo r
WHERE s.rubro_id IS NULL
  AND (
    (s.giro ILIKE 'Comercio' AND r.nombre_rubro = 'Comercio')
    OR (s.giro ILIKE 'Camara empresarial' AND r.nombre_rubro = 'Camara empresarial')
  );

-- Cualquier socio sin rubro queda en Comercio si existe
UPDATE socios s
SET rubro_id = r.id,
    giro = COALESCE(NULLIF(TRIM(s.giro), ''), r.nombre_rubro)
FROM rubros_empleo r
WHERE s.rubro_id IS NULL
  AND r.nombre_rubro = 'Comercio';

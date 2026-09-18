CREATE EXTENSION IF NOT EXISTS pgcrypto;

INSERT INTO usuarios (email, password_hash, rol)
SELECT 'admin@ccisj.org.uy', crypt('Admin12345678', gen_salt('bf', 10)), 'ADMIN'
WHERE NOT EXISTS (
    SELECT 1 FROM usuarios WHERE lower(email) = 'admin@ccisj.org.uy'
);

INSERT INTO usuarios (email, bps, password_hash, rol)
SELECT 'socio.directivo@ccisj.org.uy', '111111111', crypt('Directivo123456', gen_salt('bf', 10)), 'SOCIO'
WHERE NOT EXISTS (
    SELECT 1 FROM usuarios
    WHERE lower(email) = 'socio.directivo@ccisj.org.uy'
       OR bps = '111111111'
);

INSERT INTO usuarios (email, bps, password_hash, rol)
SELECT 'socio@ccisj.org.uy', '123456789', crypt('Socio12345678', gen_salt('bf', 10)), 'SOCIO'
WHERE NOT EXISTS (
    SELECT 1 FROM usuarios
    WHERE lower(email) = 'socio@ccisj.org.uy'
       OR bps = '123456789'
);

INSERT INTO socios (usuario_id, razon_social, rut, telefono, email_contacto, giro, es_directivo)
SELECT u.id, 'CCISJ Directivo Demo', '210000010010', '43421234', u.email, 'Camara empresarial', TRUE
FROM usuarios u
WHERE u.bps = '111111111'
  AND NOT EXISTS (SELECT 1 FROM socios s WHERE s.usuario_id = u.id);

INSERT INTO socios (usuario_id, razon_social, rut, telefono, email_contacto, giro, es_directivo)
SELECT u.id, 'Empresa Socio Demo', '210000020010', '43425678', u.email, 'Comercio', FALSE
FROM usuarios u
WHERE u.bps = '123456789'
  AND NOT EXISTS (SELECT 1 FROM socios s WHERE s.usuario_id = u.id);

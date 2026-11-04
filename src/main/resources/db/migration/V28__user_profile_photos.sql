ALTER TABLE usuarios
    ADD COLUMN foto_perfil BYTEA,
    ADD COLUMN foto_mime VARCHAR(100);

UPDATE usuarios u
SET foto_perfil = p.foto_perfil, foto_mime = p.foto_mime
FROM postulantes p
WHERE p.usuario_id = u.id AND p.foto_perfil IS NOT NULL;

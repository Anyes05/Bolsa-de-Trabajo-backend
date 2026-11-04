ALTER TABLE usuarios
    ADD COLUMN google_subject VARCHAR(255) UNIQUE,
    ADD COLUMN google_photo_url TEXT;

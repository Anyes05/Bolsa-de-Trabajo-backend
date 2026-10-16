-- RF-20: primer ingreso de socio con cambio obligatorio de clave (plazo 24 h)
ALTER TABLE usuarios
    ADD COLUMN IF NOT EXISTS debe_cambiar_password BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS password_provisional_hasta TIMESTAMP WITH TIME ZONE;

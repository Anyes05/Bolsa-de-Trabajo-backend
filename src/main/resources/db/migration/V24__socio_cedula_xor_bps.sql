-- Socios: identificación por BPS o cédula (excluyentes), nunca ambas
ALTER TABLE usuarios
    ADD COLUMN IF NOT EXISTS cedula VARCHAR(30);

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'usuarios_cedula_key'
    ) THEN
        ALTER TABLE usuarios ADD CONSTRAINT usuarios_cedula_key UNIQUE (cedula);
    END IF;
END $$;

ALTER TABLE usuarios DROP CONSTRAINT IF EXISTS usuario_identificador_check;
ALTER TABLE usuarios
    ADD CONSTRAINT usuario_identificador_check
    CHECK (email IS NOT NULL OR bps IS NOT NULL OR cedula IS NOT NULL);

ALTER TABLE usuarios DROP CONSTRAINT IF EXISTS socio_bps_xor_cedula_check;
ALTER TABLE usuarios
    ADD CONSTRAINT socio_bps_xor_cedula_check
    CHECK (
        rol <> 'SOCIO'
        OR (
            (bps IS NOT NULL AND bps <> '' AND cedula IS NULL)
            OR (cedula IS NOT NULL AND cedula <> '' AND bps IS NULL)
        )
    );

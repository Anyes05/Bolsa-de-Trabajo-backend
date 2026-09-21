-- Asegura que estos campos opcionales no bloqueen el alta de postulantes.
-- Deben poder quedar en NULL cuando no se extraen del CV/PDF.

ALTER TABLE postulantes
    ADD COLUMN IF NOT EXISTS fecha_nacimiento DATE,
    ADD COLUMN IF NOT EXISTS genero VARCHAR(30),
    ADD COLUMN IF NOT EXISTS estado_civil VARCHAR(20);

ALTER TABLE postulantes
    ALTER COLUMN fecha_nacimiento DROP NOT NULL,
    ALTER COLUMN genero DROP NOT NULL,
    ALTER COLUMN estado_civil DROP NOT NULL;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'postulantes_genero_nullable_check'
    ) THEN
        ALTER TABLE postulantes
            ADD CONSTRAINT postulantes_genero_nullable_check
            CHECK (genero IS NULL OR genero IN ('FEMENINO', 'MASCULINO', 'PREFIERO_NO_ESPECIFICAR'));
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'postulantes_estado_civil_nullable_check'
    ) THEN
        ALTER TABLE postulantes
            ADD CONSTRAINT postulantes_estado_civil_nullable_check
            CHECK (estado_civil IS NULL OR estado_civil IN ('SOLTERO', 'CASADO', 'UNION_LIBRE', 'DIVORCIADO', 'VIUDO'));
    END IF;
END $$;

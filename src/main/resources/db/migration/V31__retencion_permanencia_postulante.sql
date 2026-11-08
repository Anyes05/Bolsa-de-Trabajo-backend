-- RF-13: retención automática tras 2 meses de inactividad del postulante.
ALTER TABLE postulantes
    ADD COLUMN IF NOT EXISTS ultima_actividad_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    ADD COLUMN IF NOT EXISTS retencion_avisado_at TIMESTAMPTZ NULL,
    ADD COLUMN IF NOT EXISTS retencion_deadline_at TIMESTAMPTZ NULL,
    ADD COLUMN IF NOT EXISTS retencion_token VARCHAR(64) NULL;

CREATE UNIQUE INDEX IF NOT EXISTS ux_postulantes_retencion_token
    ON postulantes (retencion_token)
    WHERE retencion_token IS NOT NULL;

UPDATE postulantes
SET ultima_actividad_at = NOW()
WHERE ultima_actividad_at IS NULL;

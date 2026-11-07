-- RF-17: el admin elige si el comunicado genera pop-up bloqueante en el panel del socio.
ALTER TABLE comunicados
    ADD COLUMN IF NOT EXISTS popup BOOLEAN NOT NULL DEFAULT FALSE;

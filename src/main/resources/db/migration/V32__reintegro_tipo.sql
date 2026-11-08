-- RF-02: tipo de reintegro/timbre alineado a la UI (puntual vs recurrente).
ALTER TABLE reintegros
    ADD COLUMN IF NOT EXISTS tipo VARCHAR(20) NOT NULL DEFAULT 'PUNTUAL';

ALTER TABLE reintegros
    DROP CONSTRAINT IF EXISTS reintegros_tipo_check;

ALTER TABLE reintegros
    ADD CONSTRAINT reintegros_tipo_check CHECK (tipo IN ('PUNTUAL', 'RECURRENTE'));

UPDATE reintegros
SET tipo = 'RECURRENTE'
WHERE origen IS NOT NULL
  AND origen LIKE 'recurrente:%'
  AND tipo <> 'RECURRENTE';

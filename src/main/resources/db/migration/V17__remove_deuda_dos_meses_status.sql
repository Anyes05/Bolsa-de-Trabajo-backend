ALTER TABLE socios
    DROP CONSTRAINT IF EXISTS socios_estado_morosidad_check;

UPDATE socios
SET estado_morosidad = 'MOROSO'
WHERE estado_morosidad = 'DEUDA_2_MESES';

ALTER TABLE socios
    ADD CONSTRAINT socios_estado_morosidad_check
        CHECK (estado_morosidad IN ('AL_DIA', 'MOROSO', 'INACTIVO'));
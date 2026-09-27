ALTER TABLE cuotas
    DROP CONSTRAINT IF EXISTS cuotas_estado_check;

UPDATE cuotas SET estado = 'PENDIENTE' WHERE estado = 'FORZOSO';
UPDATE cuotas SET estado = 'PAGADO' WHERE estado = 'PAGADA';

ALTER TABLE cuotas
    ADD CONSTRAINT cuotas_estado_check
        CHECK (estado IN ('PENDIENTE', 'PAGADO', 'ANULADA'));

ALTER TABLE socios
    DROP CONSTRAINT IF EXISTS socios_estado_morosidad_check;

UPDATE socios SET estado_morosidad = 'DEUDA_2_MESES' WHERE estado_morosidad = 'DEUDA_A_VENCER';
UPDATE socios SET estado_morosidad = 'MOROSO' WHERE estado_morosidad = 'DEUDA_VENCIDA';

ALTER TABLE socios
    ADD CONSTRAINT socios_estado_morosidad_check
        CHECK (estado_morosidad IN ('AL_DIA', 'DEUDA_2_MESES', 'MOROSO', 'INACTIVO'));
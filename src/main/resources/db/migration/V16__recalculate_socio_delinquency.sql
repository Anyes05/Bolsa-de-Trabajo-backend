UPDATE socios socio
SET estado_morosidad = CASE
    WHEN EXISTS (
        SELECT 1
        FROM cuotas cuota
        WHERE cuota.socio_id = socio.id
            AND cuota.estado = 'PENDIENTE'
            AND cuota.fecha_vencimiento + INTERVAL '2 months' < CURRENT_DATE
    ) THEN 'MOROSO'
    WHEN EXISTS (
        SELECT 1
        FROM cuotas cuota
        WHERE cuota.socio_id = socio.id
            AND cuota.estado = 'PENDIENTE'
            AND cuota.fecha_vencimiento < CURRENT_DATE
    ) THEN 'DEUDA_2_MESES'
    ELSE 'AL_DIA'
END
WHERE socio.estado_morosidad <> 'INACTIVO';
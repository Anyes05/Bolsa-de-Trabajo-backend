package uy.ccisj.api.notificacion.dto;

import java.time.OffsetDateTime;

public record NotificacionResponseDTO(
        Long id,
        String titulo,
        String cuerpo,
        boolean leida,
        boolean popup,
        OffsetDateTime createdAt,
        Long comunicadoId
) {
}

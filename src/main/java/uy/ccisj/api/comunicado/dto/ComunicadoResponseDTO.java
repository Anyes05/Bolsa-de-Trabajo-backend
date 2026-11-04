package uy.ccisj.api.comunicado.dto;

import java.time.OffsetDateTime;
import uy.ccisj.api.domain.DestinatarioComunicado;

public record ComunicadoResponseDTO(
        Long id,
        String titulo,
        String contenido,
        DestinatarioComunicado destinatario,
        OffsetDateTime fechaPublicacion,
        String autorEmail,
        int destinatariosNotificados,
        int correosEnviados,
        int correosFallidos
) {
}

package uy.ccisj.api.comunicado.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import uy.ccisj.api.domain.DestinatarioComunicado;

public record ComunicadoCreateDTO(
        @NotBlank @Size(max = 180) String titulo,
        @NotBlank String contenido,
        @NotNull DestinatarioComunicado destinatario,
        boolean popup
) {
}

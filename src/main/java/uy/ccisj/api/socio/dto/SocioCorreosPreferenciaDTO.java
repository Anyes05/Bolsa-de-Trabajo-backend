package uy.ccisj.api.socio.dto;

import jakarta.validation.constraints.NotNull;

public record SocioCorreosPreferenciaDTO(
        @NotNull(message = "Indicá si querés silenciar los correos") Boolean silenciarCorreos) {
}

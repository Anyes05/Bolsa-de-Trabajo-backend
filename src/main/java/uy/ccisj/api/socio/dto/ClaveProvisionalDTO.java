package uy.ccisj.api.socio.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ClaveProvisionalDTO(
        @NotBlank(message = "La contraseña es obligatoria")
        @Size(min = 12, max = 72, message = "La contraseña debe tener entre 12 y 72 caracteres")
        String password) {
}

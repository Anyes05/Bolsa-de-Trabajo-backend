package uy.ccisj.api.socio.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record SocioCreateDTO(
        @NotBlank @Size(max = 30) String bps,
        @NotBlank @Email @Size(max = 254) String email,
        @NotBlank @Size(min = 12, max = 72) String password,
        @NotBlank @Size(max = 200) String razonSocial,
        @NotBlank @Size(max = 20) String rut,
        @Size(max = 160) String giro,
        @Size(max = 40) String telefono,
        @Size(max = 254) String emailContacto,
        @NotBlank @Size(max = 160) String calle,
        @Size(max = 20) String numero,
        @NotBlank @Size(max = 120) String localidad,
        @NotNull Long rubroId,
        boolean esDirectivo,
        LocalDate fechaAniversario) {
}

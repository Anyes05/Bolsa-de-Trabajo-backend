package uy.ccisj.api.socio.dto;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record SocioCreateDTO(
        @NotBlank(message = "El número de BPS es obligatorio") @Size(max = 30, message = "El BPS es demasiado largo") String bps,
        @NotBlank(message = "El correo es obligatorio") @Email(message = "El correo no es válido") @Size(max = 254) String email,
        @NotBlank(message = "La contraseña es obligatoria") @Size(min = 12, max = 72, message = "La contraseña debe tener entre 12 y 72 caracteres") String password,
        @NotBlank(message = "La razón social es obligatoria") @Size(max = 200) String razonSocial,
        @NotBlank(message = "El RUT es obligatorio") @Size(max = 20, message = "El RUT es demasiado largo") String rut,
        @Size(max = 160) String giro,
        @Size(max = 40) String telefono,
        @Size(max = 254) String emailContacto,
        @NotBlank(message = "La calle es obligatoria") @Size(max = 160) String calle,
        @Size(max = 20) String numero,
        @NotBlank(message = "La ciudad es obligatoria") @Size(max = 120) String localidad,
        @NotNull(message = "El rubro es obligatorio") Long rubroId,
        boolean esDirectivo,
        @JsonDeserialize(using = FlexibleLocalDateDeserializer.class) LocalDate fechaAniversario) {
}

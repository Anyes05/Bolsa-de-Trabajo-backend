package uy.ccisj.api.socio.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import uy.ccisj.api.domain.EstadoMorosidad;

public record SocioResponseDTO(
        Long id,
        String bps,
        String razonSocial,
        String rut,
        String giro,
        String telefono,
        String emailContacto,
        String emailUsuario,
        String calle,
        String numero,
        String localidad,
        Long rubroId,
        String nombreRubro,
        EstadoMorosidad estadoMorosidad,
        boolean esDirectivo,
        LocalDate fechaAlta,
        OffsetDateTime fechaBaja,
        LocalDate fechaAniversario,
        boolean activo) {
}

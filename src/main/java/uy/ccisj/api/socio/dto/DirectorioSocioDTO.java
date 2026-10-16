package uy.ccisj.api.socio.dto;

import java.time.LocalDate;
import uy.ccisj.api.domain.EstadoMorosidad;

public record DirectorioSocioDTO(
        Long id,
        String razonSocial,
        String rubro,
        String telefono,
        String email,
        EstadoMorosidad estadoMorosidad,
        LocalDate fechaAlta,
        boolean esDirectivo) {
}

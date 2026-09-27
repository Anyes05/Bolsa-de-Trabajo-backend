package uy.ccisj.api.socio.dto;

import java.util.List;
import uy.ccisj.api.domain.EstadoMorosidad;

public record HistorialCajaDTO(
        Long socioId,
        String razonSocial,
        String bps,
        String nombreRubro,
        EstadoMorosidad estadoMorosidad,
        List<MovimientoCajaDTO> movimientos) {
}
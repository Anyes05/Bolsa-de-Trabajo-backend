package uy.ccisj.api.socio.dto;

import java.util.List;
import java.math.BigDecimal;
import uy.ccisj.api.domain.EstadoMorosidad;

public record HistorialCajaDTO(
        Long socioId,
        String razonSocial,
        String bps,
        String nombreRubro,
        EstadoMorosidad estadoMorosidad,
        BigDecimal saldo,
        BigDecimal deudaVencida,
        List<MovimientoCajaDTO> movimientos,
        List<ReintegroProgramadoDTO> reintegrosProgramados) {
}
package uy.ccisj.api.socio.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import uy.ccisj.api.domain.EstadoCuota;
import uy.ccisj.api.domain.EstadoMorosidad;

public record CuentaCajaDTO(
        Long socioId,
        String bps,
        String razonSocial,
        String nombreRubro,
        String telefono,
        EstadoMorosidad estadoMorosidad,
        Long cuotaId,
        EstadoCuota estadoCuota,
        BigDecimal montoCuota,
        LocalDate periodo,
        LocalDate fechaVencimiento) {
}
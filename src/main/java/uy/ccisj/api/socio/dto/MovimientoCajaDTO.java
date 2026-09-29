package uy.ccisj.api.socio.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import uy.ccisj.api.domain.EstadoCuota;
import uy.ccisj.api.domain.MetodoPago;

public record MovimientoCajaDTO(
        Long cuotaId,
        LocalDate periodo,
        LocalDate fechaVencimiento,
        EstadoCuota estadoCuota,
        BigDecimal montoCuota,
        BigDecimal montoTimbre,
        boolean timbreRecurrente,
        BigDecimal montoCobrado,
        LocalDate fechaCobro,
        MetodoPago metodoPago,
        String nroCobranzaExterno,
        String observaciones) {
}
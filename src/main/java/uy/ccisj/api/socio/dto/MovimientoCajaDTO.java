package uy.ccisj.api.socio.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import uy.ccisj.api.domain.MetodoPago;

public record MovimientoCajaDTO(
        Long id,
        String tipo,
        String concepto,
        LocalDate fecha,
        LocalDate fechaVencimiento,
        BigDecimal monto,
        BigDecimal saldoAnterior,
        BigDecimal saldoResultante,
        BigDecimal pendiente,
        MetodoPago metodoPago,
        String referencia,
        String observaciones) {
}
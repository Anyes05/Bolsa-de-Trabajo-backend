package uy.ccisj.api.socio.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record TarifaDTO(
        Long id,
        int anio,
        BigDecimal montoBase,
        int diaVencimiento,
        OffsetDateTime createdAt) {
}
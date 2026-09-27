package uy.ccisj.api.socio.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

public record TarifaDTO(
        Long id,
        LocalDate periodo,
        BigDecimal montoBase,
        LocalDate fechaVencimiento,
        OffsetDateTime createdAt) {
}
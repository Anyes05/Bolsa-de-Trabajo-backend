package uy.ccisj.api.socio.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

public record GuardarTarifaDTO(
        @NotNull LocalDate periodo,
        @NotNull @DecimalMin(value = "0.01") BigDecimal montoBase,
        @NotNull LocalDate fechaVencimiento) {
}
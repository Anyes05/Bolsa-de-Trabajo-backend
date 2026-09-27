package uy.ccisj.api.socio.dto;

import java.math.BigDecimal;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record GuardarTarifaDTO(
        @Min(2000) @Max(9999) int anio,
        @NotNull @DecimalMin(value = "0.01") BigDecimal montoBase,
        @Min(1) @Max(28) int diaVencimiento) {
}
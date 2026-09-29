package uy.ccisj.api.socio.dto;

import java.math.BigDecimal;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import uy.ccisj.api.domain.MetodoPago;

public record RegistrarCobroDTO(
        @NotNull Long cuotaId,
        @NotNull MetodoPago metodoPago,
        @DecimalMin(value = "0.00") BigDecimal montoTimbre,
        Boolean timbreRecurrente,
        @Size(max = 80) String nroCobranzaExterno,
        @Size(max = 2000) String observaciones) {
}
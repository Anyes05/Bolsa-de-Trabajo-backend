package uy.ccisj.api.socio.dto;

import java.math.BigDecimal;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import uy.ccisj.api.domain.MetodoPago;

public record RegistrarCobroDTO(
        @NotNull Long socioId,
        @NotNull MetodoPago metodoPago,
        @NotNull @DecimalMin("0.01") @Digits(integer = 10, fraction = 2) BigDecimal montoRecibido,
        @Size(max = 80) String nroCobranzaExterno,
        @Size(max = 2000) String observaciones) {
}
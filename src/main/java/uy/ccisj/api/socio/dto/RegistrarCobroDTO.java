package uy.ccisj.api.socio.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import uy.ccisj.api.domain.MetodoPago;

public record RegistrarCobroDTO(
        @NotNull Long cuotaId,
        @NotNull MetodoPago metodoPago,
        @Size(max = 80) String nroCobranzaExterno,
        @Size(max = 2000) String observaciones) {
}
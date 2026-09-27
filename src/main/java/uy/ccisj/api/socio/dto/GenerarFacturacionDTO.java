package uy.ccisj.api.socio.dto;

import java.time.LocalDate;
import jakarta.validation.constraints.NotNull;

public record GenerarFacturacionDTO(@NotNull LocalDate periodo) {
}
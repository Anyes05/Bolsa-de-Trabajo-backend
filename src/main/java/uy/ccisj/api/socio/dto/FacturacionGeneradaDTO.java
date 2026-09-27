package uy.ccisj.api.socio.dto;

import java.time.LocalDate;

public record FacturacionGeneradaDTO(
        LocalDate periodo,
        Long tarifaId,
        int sociosActivos,
        int cuotasGeneradas,
        int cuotasExistentes) {
}
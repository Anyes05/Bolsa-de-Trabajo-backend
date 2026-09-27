package uy.ccisj.api.socio.dto;

import java.util.List;

public record CajaResumenDTO(
        long totalSocios,
        long alDia,
        long pendiente,
        long inactivos,
        List<CuentaCajaDTO> cuentas) {
}
package uy.ccisj.api.socio.dto;

/** Métricas de Caja. Las cuentas se obtienen paginadas en GET /admin/caja/cuentas. */
public record CajaResumenDTO(
        long totalSocios,
        long alDia,
        long pendiente,
        long inactivos) {
}

package uy.ccisj.api.socio.importacion.dto;

import java.math.BigDecimal;
import java.util.List;

public final class HistorialCajaImportacionDtos {
    private HistorialCajaImportacionDtos() {}

    public record Resumen(int total, int importables, int duplicados, int errores, int creados) {}

    public record Fila(
            int filaExcel,
            String periodo,
            BigDecimal montoCuota,
            String fechaVencimiento,
            String fechaPago,
            BigDecimal montoPagado,
            String metodoPago,
            String referencia,
            String observaciones,
            String estado,
            String motivo) {}

    public record PreviewResponse(Long socioId, String razonSocial, Resumen resumen, List<Fila> filas) {}

    public record ImportResponse(Long socioId, String razonSocial, Resumen resumen, List<Fila> filas) {}
}

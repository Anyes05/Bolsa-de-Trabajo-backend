package uy.ccisj.api.socio.importacion.dto;

import java.util.List;

public final class ImportacionDtos {
    private ImportacionDtos() {}

    public record PreviewResponse(
            ImportResumen resumen,
            List<PreviewFila> filas) {}

    public record ImportResumen(
            int total,
            int importables,
            int conFicticios,
            int duplicados,
            int errores,
            int inactivos,
            int creados) {}

    public record PreviewFila(
            int filaExcel,
            String estado,
            String motivo,
            String razonSocial,
            String bps,
            String rut,
            String telefono,
            String email,
            String calle,
            String numero,
            String localidad,
            String rubro,
            String fechaAlta,
            String fechaAniversario,
            List<String> camposFicticios,
            boolean inactivo) {}

    public record ImportResponse(
            ImportResumen resumen,
            List<PreviewFila> omitidas) {}
}

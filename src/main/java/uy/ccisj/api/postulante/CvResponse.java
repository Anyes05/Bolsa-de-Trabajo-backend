package uy.ccisj.api.postulante;

import java.time.OffsetDateTime;

public record CvResponse(
        Long id,
        Long perfilLaboralId,
        Integer version,
        String resumen,
        String nombreArchivo,
        String mimeType,
        Long sizeBytes,
        boolean activo,
        OffsetDateTime fechaCarga,
        String downloadUrl) {
}

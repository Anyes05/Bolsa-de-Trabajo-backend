package uy.ccisj.api.postulante;

import java.time.OffsetDateTime;

public record CvResponse(
        Long id,
        Long perfilLaboralId,
        String resumen,
        String nombreArchivo,
        String mimeType,
        Long sizeBytes,
        OffsetDateTime fechaCarga,
        String downloadUrl) {
}

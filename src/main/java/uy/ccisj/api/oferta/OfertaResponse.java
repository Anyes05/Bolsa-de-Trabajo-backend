package uy.ccisj.api.oferta;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import uy.ccisj.api.domain.DisponibilidadHoraria;
import uy.ccisj.api.domain.EstadoOferta;
import uy.ccisj.api.domain.TipoContrato;

public record OfertaResponse(
        Long id,
        Long socioId,
        String socioNombre,
        Long rubroId,
        String rubro,
        String titulo,
        String cargo,
        String descripcion,
        String requisitos,
        String zona,
        String salario,
        TipoContrato tipoContrato,
        DisponibilidadHoraria disponibilidadHoraria,
        int vacantes,
        OffsetDateTime fechaPublicacion,
        LocalDate fechaCierre,
        EstadoOferta estado,
        long postulaciones) {
}
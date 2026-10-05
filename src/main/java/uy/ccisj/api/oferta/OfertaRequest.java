package uy.ccisj.api.oferta;

import java.time.LocalDate;
import uy.ccisj.api.domain.DisponibilidadHoraria;
import uy.ccisj.api.domain.TipoContrato;

public record OfertaRequest(
        Long socioId,
        Long rubroId,
        String titulo,
        String cargo,
        String descripcion,
        String requisitos,
        String zona,
        String salario,
        TipoContrato tipoContrato,
        DisponibilidadHoraria disponibilidadHoraria,
        Integer vacantes,
        LocalDate fechaCierre) {
}
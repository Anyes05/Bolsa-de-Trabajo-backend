package uy.ccisj.api.postulante;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/retencion")
public class AdminRetencionController {
    private final RetencionPermanenciaService retencionPermanenciaService;

    public AdminRetencionController(RetencionPermanenciaService retencionPermanenciaService) {
        this.retencionPermanenciaService = retencionPermanenciaService;
    }

    @PostMapping("/ejecutar")
    public RetencionPermanenciaService.RetencionRunResult ejecutar() {
        return retencionPermanenciaService.procesar();
    }

    /** E2E/ops: marca al postulante como inactivo >2 meses y limpia avisos previos. */
    @PostMapping("/{id}/forzar-inactividad")
    public void forzarInactividad(@PathVariable Long id) {
        retencionPermanenciaService.forzarInactividad(id);
    }
}

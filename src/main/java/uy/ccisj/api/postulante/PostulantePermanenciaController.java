package uy.ccisj.api.postulante;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/postulante/permanencia")
public class PostulantePermanenciaController {
    private final RetencionPermanenciaService retencionPermanenciaService;

    public PostulantePermanenciaController(RetencionPermanenciaService retencionPermanenciaService) {
        this.retencionPermanenciaService = retencionPermanenciaService;
    }

    @GetMapping
    public RetencionPermanenciaService.PermanenciaEstado estado(Authentication authentication) {
        return retencionPermanenciaService.estado(authentication.getName());
    }

    @PostMapping("/confirmar")
    public RetencionPermanenciaService.PermanenciaEstado confirmar(Authentication authentication) {
        retencionPermanenciaService.confirmarAutenticado(authentication.getName());
        return retencionPermanenciaService.estado(authentication.getName());
    }
}

package uy.ccisj.api.auth;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import uy.ccisj.api.postulante.RetencionPermanenciaService;

@RestController
@RequestMapping("/auth/permanencia")
public class PermanenciaPublicController {
    private final RetencionPermanenciaService retencionPermanenciaService;

    public PermanenciaPublicController(RetencionPermanenciaService retencionPermanenciaService) {
        this.retencionPermanenciaService = retencionPermanenciaService;
    }

    @PostMapping("/confirmar")
    public void confirmar(@RequestBody TokenRequest request) {
        retencionPermanenciaService.confirmarPorToken(request == null ? null : request.token());
    }

    public record TokenRequest(String token) {}
}

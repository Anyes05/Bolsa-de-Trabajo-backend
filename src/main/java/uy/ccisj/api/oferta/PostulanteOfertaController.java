package uy.ccisj.api.oferta;

import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/postulante/ofertas")
public class PostulanteOfertaController {
    private final PostulacionService postulacionService;
    private final OfertaEmpleoService ofertaService;

    public PostulanteOfertaController(PostulacionService postulacionService, OfertaEmpleoService ofertaService) {
        this.postulacionService = postulacionService;
        this.ofertaService = ofertaService;
    }

    @GetMapping
    public List<OfertaResponse> list(Authentication authentication,
            @RequestParam(required = false) Long rubroId) {
        requireApplicant(authentication);
        return postulacionService.listOffers(rubroId);
    }

    @GetMapping("/rubros")
    public List<OfertaEmpleoService.RubroOptionResponse> sectors(Authentication authentication) {
        requireApplicant(authentication);
        return ofertaService.listOfferSectors();
    }

    @GetMapping("/postulaciones")
    public List<PostulacionService.PostulacionResponse> mine(Authentication authentication) {
        requireApplicant(authentication);
        return postulacionService.listMine(authentication.getName());
    }

    @PostMapping("/{ofertaId}/postulaciones")
    @ResponseStatus(HttpStatus.CREATED)
    public PostulacionService.PostulacionResponse apply(Authentication authentication,
            @PathVariable Long ofertaId, @RequestBody ApplyRequest request) {
        requireApplicant(authentication);
        return postulacionService.apply(authentication.getName(), ofertaId, request.perfilId());
    }

    private void requireApplicant(Authentication authentication) {
        boolean allowed = authentication != null && authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority).anyMatch("ROLE_POSTULANTE"::equals);
        if (!allowed) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Solo los postulantes pueden operar esta ruta");
    }

    public record ApplyRequest(Long perfilId) {}
}
package uy.ccisj.api.oferta;

import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import uy.ccisj.api.catalogo.RubroEmpleoRepository;
import uy.ccisj.api.domain.EstadoOferta;
import uy.ccisj.api.socio.SocioRepository;

@RestController
@RequestMapping("/ofertas")
public class OfertaEmpleoController {
    private final OfertaEmpleoService ofertaService;
    private final SocioRepository socioRepository;
    private final RubroEmpleoRepository rubroRepository;

    public OfertaEmpleoController(OfertaEmpleoService ofertaService, SocioRepository socioRepository,
            RubroEmpleoRepository rubroRepository) {
        this.ofertaService = ofertaService;
        this.socioRepository = socioRepository;
        this.rubroRepository = rubroRepository;
    }

    @GetMapping
    public List<OfertaResponse> list(Authentication authentication) {
        boolean admin = isAdmin(authentication);
        if (!admin && !isSocio(authentication)) throw forbidden();
        return ofertaService.list(authentication.getName(), admin);
    }

    @GetMapping("/socios-activos")
    public List<SocioOption> activeSocios(Authentication authentication) {
        if (!isAdmin(authentication)) throw forbidden();
        return socioRepository.findAllByOrderByRazonSocialAsc().stream()
                .filter(socio -> socio.getUsuario() != null && socio.getUsuario().isActivo())
                .map(socio -> new SocioOption(socio.getId(), socio.getRazonSocial()))
                .toList();
    }

    @GetMapping("/rubros")
    public List<RubroOption> activeRubros() {
        return rubroRepository.findByActivoTrueOrderByNombreRubroAsc().stream()
                .map(rubro -> new RubroOption(rubro.getId(), rubro.getNombreRubro()))
                .toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OfertaResponse create(Authentication authentication, @RequestBody OfertaRequest request) {
        boolean admin = isAdmin(authentication);
        if (!admin && !isSocio(authentication)) throw forbidden();
        return ofertaService.create(authentication.getName(), admin, request);
    }

    @PutMapping("/{id}")
    public OfertaResponse update(Authentication authentication, @PathVariable Long id, @RequestBody OfertaRequest request) {
        boolean admin = isAdmin(authentication);
        if (!admin && !isSocio(authentication)) throw forbidden();
        return ofertaService.update(authentication.getName(), admin, id, request);
    }

    @PatchMapping("/{id}/estado")
    public OfertaResponse setStatus(Authentication authentication, @PathVariable Long id, @RequestParam EstadoOferta estado) {
        boolean admin = isAdmin(authentication);
        if (!admin && !isSocio(authentication)) throw forbidden();
        return ofertaService.setStatus(authentication.getName(), admin, id, estado);
    }

    private boolean isAdmin(Authentication authentication) {
        return hasAuthority(authentication, "ROLE_ADMIN");
    }

    private boolean isSocio(Authentication authentication) {
        return hasAuthority(authentication, "ROLE_SOCIO");
    }

    private boolean hasAuthority(Authentication authentication, String role) {
        return authentication != null && authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority).anyMatch(role::equals);
    }

    private ResponseStatusException forbidden() {
        return new ResponseStatusException(HttpStatus.FORBIDDEN, "Rol sin permisos para gestionar ofertas");
    }

    public record SocioOption(Long id, String razonSocial) {}
    public record RubroOption(Long id, String nombreRubro) {}
}
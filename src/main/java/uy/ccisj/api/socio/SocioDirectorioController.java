package uy.ccisj.api.socio;

import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import uy.ccisj.api.common.ListQuery;
import uy.ccisj.api.common.PageResponse;
import uy.ccisj.api.domain.EstadoMorosidad;
import uy.ccisj.api.socio.dto.DirectorioSocioDTO;

@RestController
@RequestMapping("/socio/directorio")
public class SocioDirectorioController {
    private final SocioService socioService;

    public SocioDirectorioController(SocioService socioService) {
        this.socioService = socioService;
    }

    @GetMapping
    public PageResponse<DirectorioSocioDTO> page(Authentication authentication,
                                                 @RequestParam(required = false) String q,
                                                 @RequestParam(required = false) EstadoMorosidad estado,
                                                 @RequestParam(required = false) Integer page,
                                                 @RequestParam(required = false) Integer size) {
        return socioService.pageDirectorio(authentication.getName(), ListQuery.of(q, page, size), estado);
    }

    /** Directorio completo (uso interno y tests); la API expone solo la versión paginada. */
    public List<DirectorioSocioDTO> list(Authentication authentication) {
        return socioService.listDirectorio(authentication.getName());
    }
}

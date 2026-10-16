package uy.ccisj.api.socio;

import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import uy.ccisj.api.socio.dto.DirectorioSocioDTO;

@RestController
@RequestMapping("/socio/directorio")
public class SocioDirectorioController {
    private final SocioService socioService;

    public SocioDirectorioController(SocioService socioService) {
        this.socioService = socioService;
    }

    @GetMapping
    public List<DirectorioSocioDTO> list(Authentication authentication) {
        return socioService.listDirectorio(authentication.getName());
    }
}

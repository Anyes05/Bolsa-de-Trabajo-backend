package uy.ccisj.api.socio;

import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import uy.ccisj.api.socio.dto.RubroOptionDTO;
import uy.ccisj.api.socio.dto.SocioPerfilUpdateDTO;
import uy.ccisj.api.socio.dto.SocioResponseDTO;

@RestController
@RequestMapping("/socio/perfil")
public class SocioPerfilController {
    private final SocioService socioService;

    public SocioPerfilController(SocioService socioService) {
        this.socioService = socioService;
    }

    @GetMapping
    public SocioResponseDTO get(Authentication authentication) {
        return socioService.getMiPerfil(authentication.getName());
    }

    @PutMapping
    public SocioResponseDTO update(Authentication authentication, @Valid @RequestBody SocioPerfilUpdateDTO dto) {
        return socioService.updateMiPerfil(authentication.getName(), dto);
    }

    @GetMapping("/rubros")
    public List<RubroOptionDTO> rubros() {
        return socioService.listRubros();
    }
}

package uy.ccisj.api.comunicado;

import java.util.List;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import uy.ccisj.api.comunicado.dto.AudienciaCountsDTO;
import uy.ccisj.api.comunicado.dto.ComunicadoCreateDTO;
import uy.ccisj.api.comunicado.dto.ComunicadoResponseDTO;

@RestController
@RequestMapping("/admin/comunicados")
public class AdminComunicadoController {
    private final ComunicadoService comunicadoService;

    public AdminComunicadoController(ComunicadoService comunicadoService) {
        this.comunicadoService = comunicadoService;
    }

    @GetMapping
    public List<ComunicadoResponseDTO> list() {
        return comunicadoService.list();
    }

    @GetMapping("/audiencia")
    public AudienciaCountsDTO audiencia() {
        return comunicadoService.audienciaCounts();
    }

    @PostMapping
    public ComunicadoResponseDTO publish(Authentication authentication, @Valid @RequestBody ComunicadoCreateDTO dto) {
        return comunicadoService.publish(authentication.getName(), dto);
    }
}

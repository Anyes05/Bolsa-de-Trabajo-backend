package uy.ccisj.api.socio;

import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import uy.ccisj.api.socio.dto.RubroOptionDTO;
import uy.ccisj.api.socio.dto.SocioCreateDTO;
import uy.ccisj.api.socio.dto.SocioResponseDTO;
import uy.ccisj.api.socio.dto.SocioUpdateDTO;

@RestController
@RequestMapping("/admin/socios")
public class SocioController {
    private final SocioService socioService;

    public SocioController(SocioService socioService) {
        this.socioService = socioService;
    }

    @GetMapping
    public List<SocioResponseDTO> list() {
        return socioService.getAllSocios();
    }

    @GetMapping("/rubros")
    public List<RubroOptionDTO> rubros() {
        return socioService.listRubros();
    }

    @GetMapping("/{id}")
    public SocioResponseDTO get(@PathVariable Long id) {
        return socioService.getSocioById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SocioResponseDTO create(@Valid @RequestBody SocioCreateDTO dto) {
        return socioService.createSocio(dto);
    }

    @PutMapping("/{id}")
    public SocioResponseDTO update(@PathVariable Long id, @Valid @RequestBody SocioUpdateDTO dto) {
        return socioService.updateSocio(id, dto);
    }

    @DeleteMapping("/{id}")
    public SocioResponseDTO deactivate(@PathVariable Long id) {
        return socioService.deactivateSocio(id);
    }

    @PostMapping("/{id}/activar")
    public SocioResponseDTO activate(@PathVariable Long id) {
        return socioService.activateSocio(id);
    }
}

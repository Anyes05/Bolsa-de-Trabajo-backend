package uy.ccisj.api.catalogo;

import java.util.List;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/rubros")
public class RubroAdminController {
    private final RubroAdminService rubroAdminService;

    public RubroAdminController(RubroAdminService rubroAdminService) {
        this.rubroAdminService = rubroAdminService;
    }

    @GetMapping
    public List<RubroAdminDTO> list(@RequestParam(defaultValue = "true") boolean incluirInactivos) {
        return rubroAdminService.list(incluirInactivos);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RubroAdminDTO create(@Valid @RequestBody RubroNombreRequest request) {
        return rubroAdminService.create(request.nombreRubro());
    }

    @PutMapping("/{id}")
    public RubroAdminDTO update(@PathVariable Long id, @Valid @RequestBody RubroNombreRequest request) {
        return rubroAdminService.update(id, request.nombreRubro());
    }

    @PutMapping("/{id}/activo")
    public RubroAdminDTO setActivo(@PathVariable Long id, @RequestBody RubroActivoRequest request) {
        return rubroAdminService.setActivo(id, request.activo());
    }

    public record RubroNombreRequest(
            @NotBlank(message = "El nombre del rubro es obligatorio")
            @Size(max = 100, message = "El nombre del rubro es demasiado largo")
            String nombreRubro) {
    }

    public record RubroActivoRequest(boolean activo) {
    }
}

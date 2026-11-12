package uy.ccisj.api.socio;

import java.util.List;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/caja/socios/{socioId}")
public class HistorialContableController {
    private final HistorialContableService historial;

    public HistorialContableController(HistorialContableService historial) {
        this.historial = historial;
    }

    public record AnularCuota(@NotBlank @Size(max = 1000) String motivo) {}

    @GetMapping("/historial-contable")
    public List<HistorialContableService.Asiento> listar(@PathVariable Long socioId) {
        return historial.listar(socioId);
    }

    @PostMapping("/cuotas/{cuotaId}/anulacion")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void anular(@PathVariable Long socioId, @PathVariable Long cuotaId,
                       @Valid @RequestBody AnularCuota request) {
        historial.anularCuota(socioId, cuotaId, request.motivo());
    }
}

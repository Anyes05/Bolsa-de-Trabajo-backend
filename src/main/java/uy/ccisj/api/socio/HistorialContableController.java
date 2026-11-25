package uy.ccisj.api.socio;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/caja/socios/{socioId}")
public class HistorialContableController {
    private final HistorialContableService historial;

    public HistorialContableController(HistorialContableService historial) {
        this.historial = historial;
    }

    @GetMapping("/historial-contable")
    public List<HistorialContableService.Asiento> listar(@PathVariable Long socioId) {
        return historial.listar(socioId);
    }
}

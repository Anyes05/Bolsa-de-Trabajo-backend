package uy.ccisj.api.socio;

import java.util.List;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import uy.ccisj.api.socio.dto.FacturacionGeneradaDTO;
import uy.ccisj.api.socio.dto.GenerarFacturacionDTO;
import uy.ccisj.api.socio.dto.GuardarTarifaDTO;
import uy.ccisj.api.socio.dto.TarifaDTO;

@RestController
@RequestMapping("/admin/tarifas")
public class TarifaController {
    private final TarifaService tarifaService;

    public TarifaController(TarifaService tarifaService) {
        this.tarifaService = tarifaService;
    }

    @GetMapping
    public List<TarifaDTO> listar() {
        return tarifaService.listar();
    }

    @PutMapping
    public TarifaDTO guardar(@Valid @RequestBody GuardarTarifaDTO dto) {
        return tarifaService.guardar(dto);
    }

    @PostMapping("/facturacion")
    @ResponseStatus(HttpStatus.CREATED)
    public FacturacionGeneradaDTO generarFacturacion(@Valid @RequestBody GenerarFacturacionDTO dto) {
        return tarifaService.generarFacturacion(dto);
    }
}
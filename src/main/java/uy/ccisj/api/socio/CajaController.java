package uy.ccisj.api.socio;

import jakarta.validation.Valid;
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
import uy.ccisj.api.socio.dto.CajaResumenDTO;
import uy.ccisj.api.socio.dto.CuentaCajaDTO;
import uy.ccisj.api.socio.dto.HistorialCajaDTO;
import uy.ccisj.api.socio.dto.RegistrarCobroDTO;
import uy.ccisj.api.socio.dto.RegistrarReintegroDTO;
import uy.ccisj.api.socio.dto.ReintegroProgramadoDTO;

@RestController
@RequestMapping("/admin/caja")
public class CajaController {
    private final CajaService cajaService;

    public CajaController(CajaService cajaService) {
        this.cajaService = cajaService;
    }

    @GetMapping
    public CajaResumenDTO resumen() {
        return cajaService.getResumen();
    }

    @GetMapping("/socios/{socioId}/historial")
    public HistorialCajaDTO historial(@PathVariable Long socioId) {
        return cajaService.getHistorial(socioId);
    }

    @PostMapping("/cobros")
    @ResponseStatus(HttpStatus.CREATED)
    public CuentaCajaDTO registrarCobro(@Valid @RequestBody RegistrarCobroDTO dto) {
        return cajaService.registrarCobro(dto);
    }

    @PostMapping("/socios/{socioId}/reintegros")
    @ResponseStatus(HttpStatus.CREATED)
    public CuentaCajaDTO registrarReintegro(@PathVariable Long socioId, @Valid @RequestBody RegistrarReintegroDTO dto) {
        return cajaService.registrarReintegro(socioId, dto);
    }

    @PutMapping("/socios/{socioId}/reintegros-programados/{reintegroId}")
    public ReintegroProgramadoDTO actualizarProgramado(@PathVariable Long socioId, @PathVariable Long reintegroId,
                                                      @Valid @RequestBody ReintegroProgramadoDTO dto) {
        return cajaService.actualizarProgramado(socioId, reintegroId, dto);
    }

    @DeleteMapping("/socios/{socioId}/reintegros-programados/{reintegroId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminarProgramado(@PathVariable Long socioId, @PathVariable Long reintegroId) {
        cajaService.eliminarProgramado(socioId, reintegroId);
    }
}
package uy.ccisj.api.socio;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import uy.ccisj.api.socio.importacion.dto.HistorialCajaImportacionDtos.PreviewResponse;
import uy.ccisj.api.socio.importacion.dto.HistorialCajaImportacionDtos.ImportResponse;

@RestController
@RequestMapping("/admin/caja/socios/{socioId}/historial-importacion")
public class HistorialCajaImportacionController {
    private final HistorialCajaImportacionService historialCajaImportacionService;

    public HistorialCajaImportacionController(HistorialCajaImportacionService historialCajaImportacionService) {
        this.historialCajaImportacionService = historialCajaImportacionService;
    }

    @PostMapping("/preview")
    public PreviewResponse preview(@PathVariable Long socioId, @RequestParam("file") MultipartFile file) {
        return historialCajaImportacionService.preview(socioId, file);
    }

    @PostMapping
    public ImportResponse importar(@PathVariable Long socioId, @RequestParam("file") MultipartFile file) {
        return historialCajaImportacionService.importar(socioId, file);
    }
}

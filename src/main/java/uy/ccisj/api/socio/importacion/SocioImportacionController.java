package uy.ccisj.api.socio.importacion;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import uy.ccisj.api.socio.importacion.dto.ImportacionDtos.ImportResponse;
import uy.ccisj.api.socio.importacion.dto.ImportacionDtos.PreviewResponse;

@RestController
@RequestMapping("/admin/socios/importacion")
public class SocioImportacionController {
    private final SocioImportService socioImportService;

    public SocioImportacionController(SocioImportService socioImportService) {
        this.socioImportService = socioImportService;
    }

    @PostMapping("/preview")
    public PreviewResponse preview(@RequestParam("file") MultipartFile file) {
        return socioImportService.preview(file);
    }

    @PostMapping
    public ImportResponse importar(@RequestParam("file") MultipartFile file) {
        return socioImportService.importar(file);
    }
}

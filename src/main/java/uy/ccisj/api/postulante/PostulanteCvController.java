package uy.ccisj.api.postulante;

import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/postulante/cvs")
public class PostulanteCvController {
    private final CvService cvService;

    public PostulanteCvController(CvService cvService) {
        this.cvService = cvService;
    }

    @GetMapping
    public List<CvResponse> listMyCvs(Authentication authentication) {
        return cvService.listMyCvs(authentication.getName());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CvResponse uploadCv(
            Authentication authentication,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "resumen", required = false) String resumen) {
        return cvService.uploadCv(authentication.getName(), file, resumen);
    }

    @PatchMapping("/{cvId}/activar")
    public CvResponse activateCv(Authentication authentication, @PathVariable Long cvId) {
        return cvService.activateCv(authentication.getName(), cvId);
    }

    @DeleteMapping("/{cvId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteCv(Authentication authentication, @PathVariable Long cvId) {
        cvService.deleteCv(authentication.getName(), cvId);
    }

    @GetMapping("/{cvId}/download")
    public CvDownloadResponse downloadUrl(Authentication authentication, @PathVariable Long cvId) {
        return cvService.downloadLink(authentication.getName(), cvId);
    }
}

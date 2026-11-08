package uy.ccisj.api.notificacion;

import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import uy.ccisj.api.notificacion.dto.NotificacionResponseDTO;

@RestController
@RequestMapping("/postulante/notificaciones")
public class PostulanteNotificacionController {
    private final NotificacionService notificacionService;

    public PostulanteNotificacionController(NotificacionService notificacionService) {
        this.notificacionService = notificacionService;
    }

    @GetMapping
    public List<NotificacionResponseDTO> list(Authentication authentication) {
        return notificacionService.listForPostulante(authentication.getName());
    }

    @PostMapping("/{id}/leer")
    public NotificacionResponseDTO markOneRead(Authentication authentication, @PathVariable Long id) {
        return notificacionService.markOneReadPostulante(authentication.getName(), id);
    }

    @PostMapping("/marcar-leidas")
    public void markAllRead(Authentication authentication) {
        notificacionService.markAllReadPostulante(authentication.getName());
    }

    @PostMapping("/descartar-popup")
    public void dismissPopup(Authentication authentication) {
        notificacionService.dismissPopupsPostulante(authentication.getName());
    }
}

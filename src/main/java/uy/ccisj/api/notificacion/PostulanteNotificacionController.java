package uy.ccisj.api.notificacion;

import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import uy.ccisj.api.notificacion.dto.NotificacionResponseDTO;
import uy.ccisj.api.user.Role;

@RestController
@RequestMapping("/postulante/notificaciones")
public class PostulanteNotificacionController {
    private final NotificacionService notificacionService;

    public PostulanteNotificacionController(NotificacionService notificacionService) {
        this.notificacionService = notificacionService;
    }

    @GetMapping
    public List<NotificacionResponseDTO> list(Authentication authentication) {
        return notificacionService.list(authentication.getName(), Role.POSTULANTE);
    }

    @PostMapping("/{id}/leer")
    public NotificacionResponseDTO markOneRead(Authentication authentication, @PathVariable Long id) {
        return notificacionService.markOneRead(authentication.getName(), id, Role.POSTULANTE);
    }

    @PostMapping("/marcar-leidas")
    public void markAllRead(Authentication authentication) {
        notificacionService.markAllRead(authentication.getName(), Role.POSTULANTE);
    }

    @PostMapping("/descartar-popup")
    public void dismissPopup(Authentication authentication) {
        notificacionService.dismissPopups(authentication.getName(), Role.POSTULANTE);
    }
}

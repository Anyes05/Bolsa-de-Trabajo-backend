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
@RequestMapping("/socio/notificaciones")
public class SocioNotificacionController {
    private final NotificacionService notificacionService;

    public SocioNotificacionController(NotificacionService notificacionService) {
        this.notificacionService = notificacionService;
    }

    @GetMapping
    public List<NotificacionResponseDTO> list(Authentication authentication) {
        return notificacionService.listForSocio(authentication.getName());
    }

    @PostMapping("/{id}/leer")
    public NotificacionResponseDTO markOneRead(Authentication authentication, @PathVariable Long id) {
        return notificacionService.markOneRead(authentication.getName(), id);
    }

    @PostMapping("/marcar-leidas")
    public void markAllRead(Authentication authentication) {
        notificacionService.markAllRead(authentication.getName());
    }

    @PostMapping("/descartar-popup")
    public void dismissPopup(Authentication authentication) {
        notificacionService.dismissPopups(authentication.getName());
    }
}

package uy.ccisj.api.notificacion;

import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import uy.ccisj.api.notificacion.dto.NotificacionResponseDTO;
import uy.ccisj.api.user.Role;
import uy.ccisj.api.user.User;
import uy.ccisj.api.user.UserRepository;

@Service
public class NotificacionService {
    private final NotificacionRepository notificacionRepository;
    private final UserRepository userRepository;

    public NotificacionService(NotificacionRepository notificacionRepository, UserRepository userRepository) {
        this.notificacionRepository = notificacionRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public List<NotificacionResponseDTO> list(String emailOrPrincipal, Role role) {
        return listForRole(emailOrPrincipal, role);
    }

    @Transactional
    public NotificacionResponseDTO markOneRead(String emailOrPrincipal, Long id, Role role) {
        return markOneAsRead(emailOrPrincipal, id, role);
    }

    @Transactional
    public void markAllRead(String emailOrPrincipal, Role role) {
        markAllAsRead(emailOrPrincipal, role);
    }

    @Transactional
    public void dismissPopups(String emailOrPrincipal, Role role) {
        dismissAllPopups(emailOrPrincipal, role);
    }

    private List<NotificacionResponseDTO> listForRole(String emailOrPrincipal, Role role) {
        User user = requireRole(emailOrPrincipal, role);
        return notificacionRepository.findByUsuarioIdOrderByCreatedAtDesc(user.getId()).stream()
                .map(this::toDto)
                .toList();
    }

    private NotificacionResponseDTO markOneAsRead(String emailOrPrincipal, Long id, Role role) {
        User user = requireRole(emailOrPrincipal, role);
        Notificacion notificacion = notificacionRepository.findByIdAndUsuarioId(id, user.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Notificación no encontrada"));
        notificacion.setLeida(true);
        notificacion.setPopup(false);
        return toDto(notificacionRepository.save(notificacion));
    }

    private void markAllAsRead(String emailOrPrincipal, Role role) {
        User user = requireRole(emailOrPrincipal, role);
        notificacionRepository.markAllRead(user.getId());
    }

    private void dismissAllPopups(String emailOrPrincipal, Role role) {
        User user = requireRole(emailOrPrincipal, role);
        notificacionRepository.dismissPopups(user.getId());
    }

    private User requireRole(String principal, Role role) {
        return userRepository.findByEmailIgnoreCase(principal)
                .or(() -> userRepository.findByBps(principal))
                .or(() -> userRepository.findByCedula(principal))
                .filter(user -> user.isActivo() && user.getRole() == role)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "Se requiere una cuenta activa con el rol adecuado"));
    }

    private NotificacionResponseDTO toDto(Notificacion notificacion) {
        Long comunicadoId = notificacion.getComunicado() != null ? notificacion.getComunicado().getId() : null;
        return new NotificacionResponseDTO(
                notificacion.getId(),
                notificacion.getTitulo(),
                notificacion.getCuerpo(),
                notificacion.isLeida(),
                notificacion.isPopup(),
                notificacion.getCreatedAt(),
                comunicadoId);
    }
}

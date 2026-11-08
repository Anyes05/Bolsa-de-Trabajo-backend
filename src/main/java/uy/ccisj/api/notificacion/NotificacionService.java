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
    public List<NotificacionResponseDTO> listForSocio(String emailOrPrincipal) {
        return listForRole(emailOrPrincipal, Role.SOCIO);
    }

    @Transactional(readOnly = true)
    public List<NotificacionResponseDTO> listForPostulante(String emailOrPrincipal) {
        return listForRole(emailOrPrincipal, Role.POSTULANTE);
    }

    @Transactional
    public NotificacionResponseDTO markOneRead(String emailOrPrincipal, Long id) {
        return markOneRead(emailOrPrincipal, id, Role.SOCIO);
    }

    @Transactional
    public NotificacionResponseDTO markOneReadPostulante(String emailOrPrincipal, Long id) {
        return markOneRead(emailOrPrincipal, id, Role.POSTULANTE);
    }

    @Transactional
    public void markAllRead(String emailOrPrincipal) {
        markAllRead(emailOrPrincipal, Role.SOCIO);
    }

    @Transactional
    public void markAllReadPostulante(String emailOrPrincipal) {
        markAllRead(emailOrPrincipal, Role.POSTULANTE);
    }

    @Transactional
    public void dismissPopups(String emailOrPrincipal) {
        dismissPopups(emailOrPrincipal, Role.SOCIO);
    }

    @Transactional
    public void dismissPopupsPostulante(String emailOrPrincipal) {
        dismissPopups(emailOrPrincipal, Role.POSTULANTE);
    }

    private List<NotificacionResponseDTO> listForRole(String emailOrPrincipal, Role role) {
        User user = requireRole(emailOrPrincipal, role);
        return notificacionRepository.findByUsuarioIdOrderByCreatedAtDesc(user.getId()).stream()
                .map(this::toDto)
                .toList();
    }

    private NotificacionResponseDTO markOneRead(String emailOrPrincipal, Long id, Role role) {
        User user = requireRole(emailOrPrincipal, role);
        Notificacion notificacion = notificacionRepository.findByIdAndUsuarioId(id, user.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Notificación no encontrada"));
        notificacion.setLeida(true);
        notificacion.setPopup(false);
        return toDto(notificacionRepository.save(notificacion));
    }

    private void markAllRead(String emailOrPrincipal, Role role) {
        User user = requireRole(emailOrPrincipal, role);
        notificacionRepository.markAllRead(user.getId());
    }

    private void dismissPopups(String emailOrPrincipal, Role role) {
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

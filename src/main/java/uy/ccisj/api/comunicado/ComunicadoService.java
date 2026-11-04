package uy.ccisj.api.comunicado;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import uy.ccisj.api.comunicado.dto.AudienciaCountsDTO;
import uy.ccisj.api.comunicado.dto.ComunicadoCreateDTO;
import uy.ccisj.api.comunicado.dto.ComunicadoResponseDTO;
import uy.ccisj.api.domain.DestinatarioComunicado;
import uy.ccisj.api.domain.EstadoMorosidad;
import uy.ccisj.api.mail.MailNotificationService;
import uy.ccisj.api.notificacion.Notificacion;
import uy.ccisj.api.notificacion.NotificacionRepository;
import uy.ccisj.api.socio.Socio;
import uy.ccisj.api.socio.SocioRepository;
import uy.ccisj.api.user.Role;
import uy.ccisj.api.user.User;
import uy.ccisj.api.user.UserRepository;

@Service
public class ComunicadoService {
    private static final Logger LOGGER = LoggerFactory.getLogger(ComunicadoService.class);

    private final ComunicadoRepository comunicadoRepository;
    private final NotificacionRepository notificacionRepository;
    private final SocioRepository socioRepository;
    private final UserRepository userRepository;
    private final MailNotificationService mailNotificationService;

    public ComunicadoService(
            ComunicadoRepository comunicadoRepository,
            NotificacionRepository notificacionRepository,
            SocioRepository socioRepository,
            UserRepository userRepository,
            MailNotificationService mailNotificationService) {
        this.comunicadoRepository = comunicadoRepository;
        this.notificacionRepository = notificacionRepository;
        this.socioRepository = socioRepository;
        this.userRepository = userRepository;
        this.mailNotificationService = mailNotificationService;
    }

    @Transactional(readOnly = true)
    public AudienciaCountsDTO audienciaCounts() {
        return new AudienciaCountsDTO(
                socioRepository.countActivos(EstadoMorosidad.INACTIVO),
                socioRepository.countDirectivosActivos(EstadoMorosidad.INACTIVO));
    }

    @Transactional(readOnly = true)
    public List<ComunicadoResponseDTO> list() {
        return comunicadoRepository.findAllByOrderByFechaPublicacionDesc().stream()
                .map(comunicado -> toResponse(comunicado, 0, 0, 0))
                .toList();
    }

    @Transactional
    public ComunicadoResponseDTO publish(String adminEmail, ComunicadoCreateDTO dto) {
        User autor = requireAdmin(adminEmail);
        DestinatarioComunicado destinatario = normalizeAudience(dto.destinatario());
        String titulo = requireText(dto.titulo(), "El título del comunicado es obligatorio");
        String contenido = requireText(dto.contenido(), "El mensaje del comunicado es obligatorio");
        if (titulo.length() > 180) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El título no puede superar 180 caracteres");
        }

        Comunicado comunicado = comunicadoRepository.save(new Comunicado(autor, titulo, contenido, destinatario));
        List<Socio> destinatarios = resolveAudience(destinatario);
        if (destinatarios.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "No hay socios activos para la audiencia seleccionada");
        }

        List<Notificacion> notificaciones = new ArrayList<>(destinatarios.size());
        Set<String> emails = new LinkedHashSet<>();
        for (Socio socio : destinatarios) {
            User usuario = socio.getUsuario();
            if (usuario == null || !usuario.isActivo()) {
                continue;
            }
            notificaciones.add(new Notificacion(usuario, comunicado, titulo, contenido));
            String email = resolveEmail(socio);
            if (email != null) {
                emails.add(email);
            }
        }
        notificacionRepository.saveAll(notificaciones);

        int enviados = 0;
        int fallidos = 0;
        String subject = "CCISJ: " + titulo;
        String body = buildEmailBody(titulo, contenido);
        for (String email : emails) {
            try {
                mailNotificationService.send(email, subject, body);
                enviados++;
            } catch (RuntimeException exception) {
                fallidos++;
                LOGGER.warn("No se pudo enviar comunicado {} a {}: {}", comunicado.getId(), email, exception.getMessage());
            }
        }

        return toResponse(comunicado, notificaciones.size(), enviados, fallidos);
    }

    private List<Socio> resolveAudience(DestinatarioComunicado destinatario) {
        return switch (destinatario) {
            case SOLO_DIRECTIVOS -> socioRepository.findDirectivosActivosWithUsuario();
            case SOCIOS, TODOS -> socioRepository.findActivosWithUsuario();
            case POSTULANTES -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "La audiencia de postulantes no está habilitada en este flujo");
        };
    }

    private DestinatarioComunicado normalizeAudience(DestinatarioComunicado destinatario) {
        if (destinatario == DestinatarioComunicado.SOCIOS || destinatario == DestinatarioComunicado.SOLO_DIRECTIVOS) {
            return destinatario;
        }
        if (destinatario == DestinatarioComunicado.TODOS) {
            return DestinatarioComunicado.SOCIOS;
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Seleccioná 'Todos los socios' o 'Socios Directivos'");
    }

    private User requireAdmin(String email) {
        return userRepository.findByEmailIgnoreCase(email)
                .filter(user -> user.isActivo() && user.getRole() == Role.ADMIN)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "Se requiere una cuenta de administrador activa"));
    }

    private static String resolveEmail(Socio socio) {
        User user = socio.getUsuario();
        if (user != null && user.getEmail() != null && !user.getEmail().isBlank()) {
            return user.getEmail().trim().toLowerCase(Locale.ROOT);
        }
        if (socio.getEmailContacto() != null && !socio.getEmailContacto().isBlank()) {
            return socio.getEmailContacto().trim().toLowerCase(Locale.ROOT);
        }
        return null;
    }

    private static String buildEmailBody(String titulo, String contenido) {
        return """
                CENTRO COMERCIAL E INDUSTRIAL DE SAN JOSÉ

                %s

                %s

                ---
                Este aviso también está disponible en la campana de notificaciones de tu panel de socio.
                Artigas 532, San José de Mayo, Uruguay
                """.formatted(titulo, contenido);
    }

    private static String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
        }
        return value.trim();
    }

    private static ComunicadoResponseDTO toResponse(
            Comunicado comunicado,
            int destinatariosNotificados,
            int correosEnviados,
            int correosFallidos) {
        User autor = comunicado.getAutor();
        return new ComunicadoResponseDTO(
                comunicado.getId(),
                comunicado.getTitulo(),
                comunicado.getContenido(),
                comunicado.getDestinatario(),
                comunicado.getFechaPublicacion(),
                autor != null ? autor.getEmail() : null,
                destinatariosNotificados,
                correosEnviados,
                correosFallidos);
    }
}

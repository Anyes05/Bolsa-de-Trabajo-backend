package uy.ccisj.api.comunicado;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
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
    private final TransactionTemplate transactionTemplate;

    public ComunicadoService(
            ComunicadoRepository comunicadoRepository,
            NotificacionRepository notificacionRepository,
            SocioRepository socioRepository,
            UserRepository userRepository,
            MailNotificationService mailNotificationService,
            PlatformTransactionManager transactionManager) {
        this.comunicadoRepository = comunicadoRepository;
        this.notificacionRepository = notificacionRepository;
        this.socioRepository = socioRepository;
        this.userRepository = userRepository;
        this.mailNotificationService = mailNotificationService;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public AudienciaCountsDTO audienciaCounts() {
        AudienciaCountsDTO counts = transactionTemplate.execute(status -> new AudienciaCountsDTO(
                socioRepository.countActivos(EstadoMorosidad.INACTIVO),
                socioRepository.countDirectivosActivos(EstadoMorosidad.INACTIVO)));
        return counts != null ? counts : new AudienciaCountsDTO(0, 0);
    }

    public List<ComunicadoResponseDTO> list() {
        List<ComunicadoResponseDTO> items = transactionTemplate.execute(status ->
                comunicadoRepository.findAllByOrderByFechaPublicacionDesc().stream()
                        .map(comunicado -> toResponse(comunicado, 0, 0, 0, 0, List.of()))
                        .toList());
        return items != null ? items : List.of();
    }

    public ComunicadoResponseDTO publish(String adminEmail, ComunicadoCreateDTO dto) {
        mailNotificationService.requireConfigured();

        PublishDraft draft = transactionTemplate.execute(status -> persistComunicado(adminEmail, dto));
        if (draft == null) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "No se pudo publicar el comunicado");
        }

        int enviados = 0;
        int fallidos = 0;
        String subject = "CCISJ: " + draft.titulo();
        String body = buildEmailBody(draft.titulo(), draft.contenido());
        for (String email : draft.emails()) {
            try {
                mailNotificationService.send(email, subject, body);
                enviados++;
            } catch (RuntimeException exception) {
                fallidos++;
                LOGGER.warn("No se pudo enviar comunicado {} a {}: {}", draft.id(), email, exception.getMessage());
            }
        }

        LOGGER.info(
                "Comunicado {} publicado: campana={}, correosOk={}, correosFail={}, sinEmail={}",
                draft.id(), draft.notificados(), enviados, fallidos, draft.sinEmail());

        return new ComunicadoResponseDTO(
                draft.id(),
                draft.titulo(),
                draft.contenido(),
                draft.destinatario(),
                draft.popup(),
                draft.fechaPublicacion(),
                draft.autorEmail(),
                draft.notificados(),
                enviados,
                fallidos,
                draft.sinEmail(),
                draft.emails());
    }

    private PublishDraft persistComunicado(String adminEmail, ComunicadoCreateDTO dto) {
        User autor = requireAdmin(adminEmail);
        DestinatarioComunicado destinatario = normalizeAudience(dto.destinatario());
        String titulo = requireText(dto.titulo(), "El título del comunicado es obligatorio");
        String contenido = requireText(dto.contenido(), "El mensaje del comunicado es obligatorio");
        if (titulo.length() > 180) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El título no puede superar 180 caracteres");
        }
        boolean popup = dto.popup();

        Comunicado comunicado = comunicadoRepository.save(new Comunicado(autor, titulo, contenido, destinatario, popup));
        List<Socio> destinatarios = resolveAudience(destinatario);
        if (destinatarios.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "No hay socios activos para la audiencia seleccionada");
        }

        List<Notificacion> notificaciones = new ArrayList<>(destinatarios.size());
        Set<String> emails = new LinkedHashSet<>();
        int sinEmail = 0;
        for (Socio socio : destinatarios) {
            User usuario = socio.getUsuario();
            if (usuario == null || !usuario.isActivo()) {
                continue;
            }
            notificaciones.add(new Notificacion(usuario, comunicado, titulo, contenido, popup));
            String email = resolveContactEmail(socio);
            if (email != null) {
                emails.add(email);
            } else {
                sinEmail++;
                LOGGER.warn("Socio {} ({}) sin email de contacto ni de cuenta", socio.getId(), socio.getRazonSocial());
            }
        }
        if (notificaciones.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "No hay socios activos con cuenta para notificar");
        }
        notificacionRepository.saveAll(notificaciones);
        comunicadoRepository.flush();
        notificacionRepository.flush();

        return new PublishDraft(
                comunicado.getId(),
                titulo,
                contenido,
                destinatario,
                popup,
                comunicado.getFechaPublicacion(),
                autor.getEmail(),
                notificaciones.size(),
                sinEmail,
                List.copyOf(emails));
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

    /** Prioridad: email de contacto del socio, luego email de la cuenta de acceso. */
    static String resolveContactEmail(Socio socio) {
        if (socio.getEmailContacto() != null && !socio.getEmailContacto().isBlank()) {
            return socio.getEmailContacto().trim().toLowerCase(Locale.ROOT);
        }
        User user = socio.getUsuario();
        if (user != null && user.getEmail() != null && !user.getEmail().isBlank()) {
            return user.getEmail().trim().toLowerCase(Locale.ROOT);
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
            int correosFallidos,
            int sociosSinEmail,
            List<String> correosDestino) {
        User autor = comunicado.getAutor();
        return new ComunicadoResponseDTO(
                comunicado.getId(),
                comunicado.getTitulo(),
                comunicado.getContenido(),
                comunicado.getDestinatario(),
                comunicado.isPopup(),
                comunicado.getFechaPublicacion(),
                autor != null ? autor.getEmail() : null,
                destinatariosNotificados,
                correosEnviados,
                correosFallidos,
                sociosSinEmail,
                correosDestino);
    }

    private record PublishDraft(
            Long id,
            String titulo,
            String contenido,
            DestinatarioComunicado destinatario,
            boolean popup,
            OffsetDateTime fechaPublicacion,
            String autorEmail,
            int notificados,
            int sinEmail,
            List<String> emails) {
    }
}

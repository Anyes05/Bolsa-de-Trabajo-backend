package uy.ccisj.api.postulante;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import uy.ccisj.api.mail.MailNotificationService;
import uy.ccisj.api.notificacion.Notificacion;
import uy.ccisj.api.notificacion.NotificacionRepository;
import uy.ccisj.api.user.Role;
import uy.ccisj.api.user.User;

@Service
public class RetencionPermanenciaService {
    static final String TITULO = "Confirmá tu permanencia en la Bolsa";
    private static final ZoneId ZONE = ZoneId.of("America/Montevideo");
    private static final int MESES_INACTIVIDAD = 2;
    private static final int DIAS_GRACIA = 7;
    private static final Logger LOGGER = LoggerFactory.getLogger(RetencionPermanenciaService.class);

    private final PostulanteRepository postulanteRepository;
    private final NotificacionRepository notificacionRepository;
    private final MailNotificationService mailNotificationService;
    private final String webUrl;

    public RetencionPermanenciaService(
            PostulanteRepository postulanteRepository,
            NotificacionRepository notificacionRepository,
            MailNotificationService mailNotificationService,
            @Value("${app.web-url:https://ccisj.cloud-ip.cc}") String webUrl) {
        this.postulanteRepository = postulanteRepository;
        this.notificacionRepository = notificacionRepository;
        this.mailNotificationService = mailNotificationService;
        this.webUrl = webUrl == null || webUrl.isBlank() ? "https://ccisj.cloud-ip.cc" : webUrl.trim().replaceAll("/$", "");
    }

    @Transactional
    public void touchActividad(User user) {
        if (user == null || user.getRole() != Role.POSTULANTE || !user.isActivo()) {
            return;
        }
        postulanteRepository.findByUsuarioId(user.getId()).ifPresent(postulante -> {
            postulante.setUltimaActividadAt(OffsetDateTime.now(ZONE));
            // Volver a entrar cuenta como confirmación de permanencia (reactiva si estaba oculto).
            if (postulante.getRetencionAvisadoAt() != null || !postulante.isVisible()) {
                postulante.setVisible(true);
                clearRetencion(postulante);
            }
            postulanteRepository.save(postulante);
        });
    }

    @Transactional
    public RetencionRunResult procesar() {
        OffsetDateTime ahora = OffsetDateTime.now(ZONE);
        OffsetDateTime limite = ahora.minusMonths(MESES_INACTIVIDAD);
        int avisos = 0;
        int mailsOk = 0;
        int mailsFail = 0;
        int ocultados = 0;

        List<Postulante> aAvisar = postulanteRepository.findPendientesDeAviso(limite);
        for (Postulante postulante : aAvisar) {
            User usuario = postulante.getUsuario();
            if (usuario == null || !usuario.isActivo()) {
                continue;
            }
            String token = UUID.randomUUID().toString().replace("-", "");
            postulante.setRetencionToken(token);
            postulante.setRetencionAvisadoAt(ahora);
            postulante.setRetencionDeadlineAt(ahora.plusDays(DIAS_GRACIA));
            postulanteRepository.save(postulante);

            String cuerpo = buildBody(postulante.getNombreCompleto(), postulante.getRetencionDeadlineAt(), token);
            notificacionRepository.save(new Notificacion(usuario, TITULO, cuerpo, true));
            avisos++;

            String email = resolveEmail(usuario);
            if (email == null) {
                LOGGER.warn("Retención postulante id={}: sin email", postulante.getId());
                continue;
            }
            if (!mailNotificationService.isConfigured()) {
                LOGGER.warn("Retención postulante id={}: SMTP no configurado; solo campana", postulante.getId());
                continue;
            }
            try {
                mailNotificationService.send(
                        email,
                        "CCISJ — Confirmá tu permanencia en la Bolsa",
                        buildEmail(
                                postulante.getNombreCompleto(),
                                postulante.getRetencionDeadlineAt(),
                                token));
                mailsOk++;
            } catch (RuntimeException exception) {
                mailsFail++;
                LOGGER.warn("Retención postulante id={}: fallo mail a {}: {}",
                        postulante.getId(), email, exception.getMessage());
            }
        }

        List<Postulante> aOcultar = postulanteRepository.findPendientesDeOcultar(ahora);
        for (Postulante postulante : aOcultar) {
            postulante.setVisible(false);
            postulante.setRetencionToken(null);
            postulanteRepository.save(postulante);
            ocultados++;
            LOGGER.info("RF-13: ocultado postulante id={} por no confirmar permanencia", postulante.getId());
        }

        LOGGER.info("Retención: avisos={}, mailsOk={}, mailsFail={}, ocultados={}",
                avisos, mailsOk, mailsFail, ocultados);
        return new RetencionRunResult(avisos, mailsOk, mailsFail, ocultados);
    }

    @Transactional
    public PermanenciaEstado estado(String email) {
        Postulante postulante = requirePostulante(email);
        boolean pendiente = postulante.getRetencionAvisadoAt() != null
                && postulante.getRetencionDeadlineAt() != null
                && postulante.isVisible()
                && !OffsetDateTime.now(ZONE).isAfter(postulante.getRetencionDeadlineAt());
        boolean ocultoPorRetencion = !postulante.isVisible() && postulante.getRetencionAvisadoAt() != null;
        return new PermanenciaEstado(
                pendiente || ocultoPorRetencion,
                pendiente,
                ocultoPorRetencion,
                postulante.getRetencionDeadlineAt());
    }

    @Transactional
    public void confirmarAutenticado(String email) {
        confirmar(requirePostulante(email));
    }

    @Transactional
    public void confirmarPorToken(String token) {
        if (token == null || token.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Token de permanencia inválido");
        }
        Postulante postulante = postulanteRepository.findByRetencionToken(token.trim())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "El enlace de confirmación no es válido o ya fue usado"));
        confirmar(postulante);
    }

    /** Ops/E2E: simula 2+ meses de inactividad sin aviso previo. */
    @Transactional
    public void forzarInactividad(Long postulanteId) {
        Postulante postulante = postulanteRepository.findById(postulanteId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Postulante no encontrado"));
        postulante.setUltimaActividadAt(OffsetDateTime.now(ZONE).minusMonths(MESES_INACTIVIDAD).minusDays(1));
        clearRetencion(postulante);
        postulante.setVisible(true);
        postulanteRepository.save(postulante);
    }

    private void confirmar(Postulante postulante) {
        postulante.setVisible(true);
        postulante.setUltimaActividadAt(OffsetDateTime.now(ZONE));
        clearRetencion(postulante);
        postulanteRepository.save(postulante);
    }

    private static void clearRetencion(Postulante postulante) {
        postulante.setRetencionAvisadoAt(null);
        postulante.setRetencionDeadlineAt(null);
        postulante.setRetencionToken(null);
    }

    private Postulante requirePostulante(String email) {
        return postulanteRepository.findByUsuarioEmailIgnoreCase(email)
                .filter(p -> p.getUsuario() != null && p.getUsuario().isActivo()
                        && p.getUsuario().getRole() == Role.POSTULANTE)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "Se requiere una cuenta de postulante activa"));
    }

    private static String resolveEmail(User user) {
        if (user.getEmail() == null || user.getEmail().isBlank()) {
            return null;
        }
        return user.getEmail().trim().toLowerCase(Locale.ROOT);
    }

    private String buildBody(String nombre, OffsetDateTime deadline, String token) {
        String quien = nombre == null || nombre.isBlank() ? "postulante" : nombre.trim();
        String hasta = deadline == null ? "pronto" : deadline.toLocalDate().toString();
        String link = webUrl + "/permanencia?token=" + token;
        return """
                Hola %s,

                Hace más de 2 meses que no ingresás a la Bolsa de Empleo del CCISJ.
                Para seguir visible ante las empresas socias, confirmá tu permanencia antes del %s.

                Podés hacerlo iniciando sesión o con este enlace:
                %s

                Si no confirmás, tu perfil dejará de mostrarse en la bolsa.

                Equipo CCISJ
                """.formatted(quien, hasta, link);
    }

    private uy.ccisj.api.mail.MailTemplate.Content buildEmail(
            String nombre, OffsetDateTime deadline, String token) {
        String quien = nombre == null || nombre.isBlank() ? "postulante" : nombre.trim();
        String hasta = deadline == null
                ? "pronto"
                : deadline.toLocalDate().format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"));
        String link = webUrl + "/permanencia?token=" + token;
        return uy.ccisj.api.mail.MailTemplate.build(
                "Confirmá tu permanencia",
                "Hola, " + quien + ":",
                java.util.List.of(
                        "Hace más de 2 meses que no ingresás a la Bolsa de Empleo del CCISJ.",
                        "Para seguir visible ante las empresas socias, confirmá tu permanencia antes de la fecha límite.",
                        "Si no confirmás a tiempo, tu perfil dejará de mostrarse en la bolsa."),
                java.util.List.of(
                        new uy.ccisj.api.mail.MailTemplate.Detail("Fecha límite", hasta)),
                new uy.ccisj.api.mail.MailTemplate.Cta("Confirmar permanencia", link),
                "También podés confirmar iniciando sesión en la Bolsa de Empleo.");
    }

    public record RetencionRunResult(int avisos, int mailsOk, int mailsFail, int ocultados) {}

    public record PermanenciaEstado(
            boolean requiereAccion,
            boolean avisoPendiente,
            boolean ocultoPorRetencion,
            OffsetDateTime deadline) {}
}

package uy.ccisj.api.socio;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uy.ccisj.api.mail.MailNotificationService;
import uy.ccisj.api.notificacion.Notificacion;
import uy.ccisj.api.notificacion.NotificacionRepository;
import uy.ccisj.api.user.User;

@Service
public class AniversarioService {
    static final String TITULO = "Feliz aniversario — CCISJ";
    private static final ZoneId ZONE = ZoneId.of("America/Montevideo");
    private static final Logger LOGGER = LoggerFactory.getLogger(AniversarioService.class);

    private final SocioRepository socioRepository;
    private final NotificacionRepository notificacionRepository;
    private final MailNotificationService mailNotificationService;

    public AniversarioService(
            SocioRepository socioRepository,
            NotificacionRepository notificacionRepository,
            MailNotificationService mailNotificationService) {
        this.socioRepository = socioRepository;
        this.notificacionRepository = notificacionRepository;
        this.mailNotificationService = mailNotificationService;
    }

    /**
     * RF-15: campana in-app + Gmail (best-effort) para socios activos cuyo
     * {@code fechaAniversario} coincide con el día (mes/día), no la fecha de alta.
     */
    @Transactional
    public AniversarioRunResult procesarAniversariosDelDia(LocalDate hoy) {
        LocalDate date = hoy != null ? hoy : LocalDate.now(ZONE);
        List<Integer> days = anniversaryDaysFor(date);
        List<Socio> candidatos = socioRepository.findActivosWithAnniversaryOn(date.getMonthValue(), days);
        int campana = 0;
        int mailsOk = 0;
        int mailsFail = 0;
        int omitidos = 0;

        OffsetDateTime dayStart = date.atStartOfDay(ZONE).toOffsetDateTime();
        OffsetDateTime dayEnd = date.plusDays(1).atStartOfDay(ZONE).toOffsetDateTime();

        for (Socio socio : candidatos) {
            User usuario = socio.getUsuario();
            if (usuario == null || !usuario.isActivo()) {
                omitidos++;
                continue;
            }
            if (notificacionRepository.existsByUsuarioIdAndTituloAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                    usuario.getId(), TITULO, dayStart, dayEnd)) {
                omitidos++;
                continue;
            }

            String cuerpo = buildBody(socio.getRazonSocial());
            notificacionRepository.save(new Notificacion(usuario, TITULO, cuerpo, false));
            campana++;

            String email = resolveContactEmail(socio);
            if (email == null) {
                LOGGER.warn("Aniversario socio id={}: sin email de contacto ni de cuenta", socio.getId());
                continue;
            }
            if (!mailNotificationService.isConfigured()) {
                LOGGER.warn("Aniversario socio id={}: SMTP no configurado; solo campana", socio.getId());
                continue;
            }
            try {
                mailNotificationService.send(email, "CCISJ — " + TITULO, buildEmail(socio.getRazonSocial()));
                mailsOk++;
            } catch (RuntimeException exception) {
                mailsFail++;
                LOGGER.warn("Aniversario socio id={}: fallo mail a {}: {}",
                        socio.getId(), email, exception.getMessage());
            }
        }

        LOGGER.info(
                "Aniversarios {}: candidatos={}, campana={}, mailsOk={}, mailsFail={}, omitidos={}",
                date, candidatos.size(), campana, mailsOk, mailsFail, omitidos);
        return new AniversarioRunResult(date, candidatos.size(), campana, mailsOk, mailsFail, omitidos);
    }

    /** 29-feb en años no bisiestos se felicita el 28-feb. */
    static List<Integer> anniversaryDaysFor(LocalDate date) {
        List<Integer> days = new ArrayList<>();
        days.add(date.getDayOfMonth());
        if (date.getMonthValue() == 2
                && date.getDayOfMonth() == 28
                && !date.isLeapYear()) {
            days.add(29);
        }
        return days;
    }

    private static String buildBody(String razonSocial) {
        String nombre = razonSocial == null || razonSocial.isBlank() ? "socio/a" : razonSocial.trim();
        return """
                ¡Feliz aniversario, %s!

                Desde el Centro Comercial e Industrial de San José te enviamos un saludo
                especial por tu aniversario. Gracias por formar parte de nuestra comunidad.

                Equipo CCISJ
                """.formatted(nombre);
    }

    private static uy.ccisj.api.mail.MailTemplate.Content buildEmail(String razonSocial) {
        String nombre = razonSocial == null || razonSocial.isBlank() ? "socio/a" : razonSocial.trim();
        return uy.ccisj.api.mail.MailTemplate.build(
                "¡Feliz aniversario!",
                "Hola, " + nombre + ":",
                java.util.List.of(
                        "Desde el Centro Comercial e Industrial de San José queremos saludarte por tu aniversario.",
                        "Gracias por formar parte de nuestra comunidad. ¡Que tengas un excelente día!"),
                java.util.List.of(),
                null,
                "También vas a ver este saludo en la campana de notificaciones de tu panel de socio.");
    }

    private static String resolveContactEmail(Socio socio) {
        if (socio.getEmailContacto() != null && !socio.getEmailContacto().isBlank()) {
            return socio.getEmailContacto().trim().toLowerCase(Locale.ROOT);
        }
        User user = socio.getUsuario();
        if (user != null && user.getEmail() != null && !user.getEmail().isBlank()) {
            return user.getEmail().trim().toLowerCase(Locale.ROOT);
        }
        return null;
    }

    public record AniversarioRunResult(
            LocalDate fecha,
            int candidatos,
            int campana,
            int mailsOk,
            int mailsFail,
            int omitidos) {}
}

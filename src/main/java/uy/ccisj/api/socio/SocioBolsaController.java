package uy.ccisj.api.socio;

import java.time.Duration;
import java.util.List;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import uy.ccisj.api.auth.ProfilePhotoService;
import uy.ccisj.api.mail.MailNotificationService;
import uy.ccisj.api.mail.MailTemplate;
import uy.ccisj.api.notificacion.Notificacion;
import uy.ccisj.api.notificacion.NotificacionRepository;
import uy.ccisj.api.storage.S3StorageService;
import uy.ccisj.api.user.Role;
import uy.ccisj.api.user.UserRepository;

@RestController
@RequestMapping("/socio/bolsa")
public class SocioBolsaController {
    private final JdbcTemplate jdbcTemplate;
    private final UserRepository userRepository;
    private final SocioRepository socioRepository;
        private final S3StorageService storageService;
        private final MailNotificationService mailNotificationService;
        private final NotificacionRepository notificacionRepository;
        private final int presignedMinutes;

    public SocioBolsaController(JdbcTemplate jdbcTemplate, UserRepository userRepository,
                        SocioRepository socioRepository, S3StorageService storageService,
                        MailNotificationService mailNotificationService,
                        NotificacionRepository notificacionRepository,
                        @Value("${app.storage.presigned-minutes:15}") int presignedMinutes) {
        this.jdbcTemplate = jdbcTemplate;
        this.userRepository = userRepository;
        this.socioRepository = socioRepository;
        this.storageService = storageService;
        this.mailNotificationService = mailNotificationService;
        this.notificacionRepository = notificacionRepository;
        this.presignedMinutes = presignedMinutes;
    }

    @GetMapping
    public List<ApplicantProfileCard> search(Authentication authentication,
            @RequestParam(required = false) String rubro) {
        Long socioId = requireEligibleSocio(authentication.getName());
        return jdbcTemplate.query("""
                SELECT pl.id, p.id AS postulante_id, p.nombre_completo, u.email, p.telefono, pl.nombre AS perfil_nombre, p.zona_residencia,
                       pl.disponibilidad_horaria, pl.libreta, pl.tiene_vehiculo,
                       COALESCE(string_agg(DISTINCT r.nombre_rubro, '|'), '') AS rubros,
                       CASE WHEN application.id IS NOT NULL THEN p.cedula_identidad END AS cedula_identidad,
                       CASE WHEN application.id IS NOT NULL
                              THEN EXTRACT(YEAR FROM age(current_date, p.fecha_nacimiento))::integer END AS edad,
                       CASE WHEN application.id IS NOT NULL THEN pl.descripcion_experiencia END AS descripcion_experiencia,
                       CASE WHEN application.id IS NOT NULL THEN pl.ultimo_empleo END AS ultimo_empleo,
                       application.id IS NOT NULL AS tiene_postulacion,
                       cv.id IS NOT NULL AS tiene_cv,
                       cv.nombre_archivo, cv.size_bytes, u.foto_perfil, u.foto_mime, u.google_photo_url
                  FROM perfiles_laborales pl
                  JOIN postulantes p ON p.id = pl.postulante_id
                  JOIN usuarios u ON u.id = p.usuario_id AND u.activo = TRUE
                  LEFT JOIN perfil_rubros pr ON pr.perfil_id = pl.id
                  LEFT JOIN rubros_empleo r ON r.id = pr.rubro_id
                  LEFT JOIN LATERAL (
                      SELECT a.id, a.cv_id FROM postulaciones a
                      JOIN ofertas_empleo o ON o.id = a.oferta_id
                      WHERE a.postulante_id = p.id AND a.perfil_laboral_id = pl.id
                        AND o.socio_id = ? AND a.cv_id IS NOT NULL
                      ORDER BY a.fecha DESC LIMIT 1
                  ) application ON TRUE
                  LEFT JOIN cvs cv ON cv.id = application.cv_id
                 WHERE pl.visible = TRUE AND p.visible = TRUE AND p.busqueda_pausada = FALSE
                   AND (CAST(? AS VARCHAR) IS NULL OR EXISTS (
                       SELECT 1 FROM perfil_rubros filter_pr
                       JOIN rubros_empleo filter_r ON filter_r.id = filter_pr.rubro_id
                       WHERE filter_pr.perfil_id = pl.id AND filter_r.nombre_rubro = ?))
                 GROUP BY p.id, p.nombre_completo, u.email, p.telefono, pl.id, pl.nombre, p.zona_residencia,
                          pl.disponibilidad_horaria, pl.libreta, pl.tiene_vehiculo,
                          p.cedula_identidad, p.fecha_nacimiento, pl.descripcion_experiencia,
                          pl.ultimo_empleo, application.id, cv.id, cv.nombre_archivo, cv.size_bytes,
                          u.foto_perfil, u.foto_mime, u.google_photo_url
                 ORDER BY pl.id DESC
                """, (rs, rowNum) -> {
                    String fullName = rs.getString("nombre_completo");
                    List<String> sectors = splitRubros(rs.getString("rubros"));
                    String availability = rs.getString("disponibilidad_horaria");
                    return new ApplicantProfileCard(rs.getLong("id"), rs.getLong("postulante_id"), fullName,
                            rs.getString("email"), rs.getString("telefono"), initials(fullName),
                            rs.getString("perfil_nombre"), sectors.isEmpty() ? "OTROS" : sectors.getFirst().toUpperCase(),
                            rs.getString("zona_residencia"), sectors, availability, rs.getString("libreta"),
                            rs.getBoolean("tiene_vehiculo"), rs.getBoolean("tiene_postulacion"),
                            rs.getBoolean("tiene_cv"),
                            rs.getString("cedula_identidad"), (Integer) rs.getObject("edad"),
                            rs.getString("descripcion_experiencia"), rs.getString("ultimo_empleo"),
                            rs.getString("nombre_archivo"), cvMeta(rs.getLong("size_bytes"), rs.wasNull()),
                            ProfilePhotoService.photoUrl(rs.getBytes("foto_perfil"), rs.getString("foto_mime"),
                                    rs.getString("google_photo_url")));
                }, socioId, rubro == null || rubro.isBlank() ? null : rubro.trim(),
                        rubro == null || rubro.isBlank() ? null : rubro.trim());
    }

    @GetMapping("/{profileId}/cvs")
    public List<ProfileCv> listCvs(Authentication authentication, @PathVariable Long profileId) {
        requireProfileApplication(profileId, requireEligibleSocio(authentication.getName()));
        return jdbcTemplate.query("""
                SELECT id, nombre_archivo, size_bytes, activo, fecha_carga
                  FROM cvs
                 WHERE perfil_laboral_id = ?
                 ORDER BY activo DESC, fecha_carga DESC, id DESC
                """, (rs, rowNum) -> new ProfileCv(rs.getLong("id"), rs.getString("nombre_archivo"),
                        cvMeta(rs.getLong("size_bytes"), rs.wasNull()), rs.getBoolean("activo"),
                        rs.getObject("fecha_carga", java.time.OffsetDateTime.class)), profileId);
    }

    @GetMapping("/{profileId}/cvs/{cvId}")
    public CvLink downloadCv(Authentication authentication, @PathVariable Long profileId, @PathVariable Long cvId) {
        requireProfileApplication(profileId, requireEligibleSocio(authentication.getName()));
        String key = jdbcTemplate.query("""
                SELECT ruta_archivo_cv
                  FROM cvs
                 WHERE id = ? AND perfil_laboral_id = ?
                """, rs -> rs.next() ? rs.getString(1) : null, cvId, profileId);
        if (key == null || key.isBlank()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "CV no encontrado para este perfil");
        }
        return new CvLink(storageService.presignedDownloadUrl(key, Duration.ofMinutes(presignedMinutes)));
    }

    @GetMapping("/postulantes/{postulanteId}/postulaciones")
    public List<ContactApplication> contactApplications(Authentication authentication, @PathVariable Long postulanteId) {
        Long socioId = requireEligibleSocio(authentication.getName());
        return jdbcTemplate.query("""
                SELECT o.id AS oferta_id, o.titulo AS oferta_titulo, pl.id AS perfil_id, pl.nombre AS perfil_nombre
                  FROM postulaciones a
                  JOIN ofertas_empleo o ON o.id = a.oferta_id AND o.socio_id = ?
                  JOIN perfiles_laborales pl ON pl.id = a.perfil_laboral_id AND pl.visible = TRUE
                  JOIN postulantes p ON p.id = a.postulante_id AND p.id = pl.postulante_id AND p.visible = TRUE
                  JOIN usuarios u ON u.id = p.usuario_id AND u.activo = TRUE
                 WHERE p.id = ?
                 ORDER BY a.fecha DESC, a.id DESC
                """, (rs, rowNum) -> new ContactApplication(
                        rs.getLong("oferta_id"), rs.getString("oferta_titulo"),
                        rs.getLong("perfil_id"), rs.getString("perfil_nombre")), socioId, postulanteId);
    }

        @PostMapping("/{profileId}/notificacion")
        @Transactional
        public ContactNotification notifyApplicant(Authentication authentication, @PathVariable Long profileId,
            @Valid @RequestBody ContactNotificationRequest request) {
        Long socioId = requireEligibleSocio(authentication.getName());
        ContactRecipient recipient = jdbcTemplate.query("""
            SELECT u.id AS postulante_usuario_id, p.nombre_completo AS postulante_nombre, u.email AS postulante_email,
                   s.razon_social, COALESCE(NULLIF(s.email_contacto, ''), socio_usuario.email) AS socio_email,
                   o.titulo AS oferta_titulo
              FROM perfiles_laborales pl
              JOIN postulantes p ON p.id = pl.postulante_id AND p.visible = TRUE
              JOIN usuarios u ON u.id = p.usuario_id AND u.activo = TRUE
              JOIN socios s ON s.id = ?
              JOIN usuarios socio_usuario ON socio_usuario.id = s.usuario_id AND socio_usuario.activo = TRUE
              JOIN postulaciones a ON a.perfil_laboral_id = pl.id AND a.postulante_id = p.id
              JOIN ofertas_empleo o ON o.id = a.oferta_id AND o.socio_id = s.id AND o.id = ?
             WHERE pl.id = ? AND pl.visible = TRUE
            """, rs -> rs.next()
                ? new ContactRecipient(rs.getLong("postulante_usuario_id"), rs.getString("postulante_nombre"), rs.getString("postulante_email"),
                    rs.getString("razon_social"), rs.getString("socio_email"), rs.getString("oferta_titulo"))
                : null, socioId, request.ofertaId(), profileId);
        if (recipient == null || recipient.applicantEmail() == null || recipient.applicantEmail().isBlank()
            || recipient.socioEmail() == null || recipient.socioEmail().isBlank()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "No se encontró una postulación a esta oferta de tu empresa con datos de contacto disponibles");
        }

        boolean selected = request.selected();
        String title = selected ? "Avance en el proceso de selección" : "Resultado del proceso de selección";
        String subject = "CCISJ — " + title + " — " + recipient.offerTitle();
        String outcome = selected
                ? "Le informamos que su postulación para la oferta «" + recipient.offerTitle()
                        + "» de " + recipient.socioName() + " ha sido seleccionada para continuar en el proceso de selección."
                : "Le informamos que, en esta oportunidad, su postulación para la oferta «"
                        + recipient.offerTitle() + "» de " + recipient.socioName()
                        + " no ha sido seleccionada para continuar en el proceso.";
        var content = MailTemplate.build(
                title,
                "Estimado/a " + recipient.applicantName() + ":",
                java.util.List.of(
                        outcome,
                        selected
                                ? "Esta comunicación confirma el avance de su candidatura y no constituye una confirmación de contratación."
                                : "Agradecemos su interés y el tiempo dedicado a presentar su candidatura. Le invitamos a consultar otras oportunidades en la Bolsa de Empleo del CCISJ.",
                        "Para realizar consultas sobre este proceso, puede comunicarse con la empresa a través de "
                                + recipient.socioEmail() + ". Si responde a este correo, su mensaje será dirigido a la empresa."),
                java.util.List.of(
                        new MailTemplate.Detail("Oferta laboral", recipient.offerTitle()),
                        new MailTemplate.Detail("Empresa", recipient.socioName()),
                        new MailTemplate.Detail("Resultado", selected ? "Seleccionado/a para la siguiente etapa" : "No seleccionado/a"),
                        new MailTemplate.Detail("Correo de la empresa", recipient.socioEmail())),
                null,
                "Comunicación emitida a solicitud de la empresa a través de la Bolsa de Empleo del Centro Comercial e Industrial de San José.");
        notificacionRepository.saveAndFlush(new Notificacion(
                userRepository.getReferenceById(recipient.applicantUserId()), title, content.plain(), false));
        mailNotificationService.send(recipient.applicantEmail(), subject, content, recipient.socioEmail());
        return new ContactNotification("Notificación enviada a " + recipient.applicantEmail());
        }

    private void requireProfileApplication(Long profileId, Long socioId) {
        Boolean hasApplication = jdbcTemplate.queryForObject("""
                SELECT EXISTS (
                    SELECT 1
                      FROM postulaciones a
                      JOIN ofertas_empleo o ON o.id = a.oferta_id
                     WHERE a.perfil_laboral_id = ? AND o.socio_id = ?
                )
                """, Boolean.class, profileId, socioId);
        if (!Boolean.TRUE.equals(hasApplication)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "El perfil no se postuló a una oferta de tu empresa");
        }
    }

    private Long requireEligibleSocio(String email) {
        var user = userRepository.findByEmailIgnoreCase(email)
                .filter(candidate -> candidate.isActivo() && candidate.getRole() == Role.SOCIO)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "Se requiere una cuenta de socio activa"));
        var socio = socioRepository.findByUsuarioId(user.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Socio no encontrado"));
        return socio.getId();
    }

    private List<String> splitRubros(String value) {
        if (value == null || value.isBlank()) return List.of();
        return java.util.Arrays.stream(value.split("\\|"))
                .filter(item -> !item.isBlank())
                .toList();
    }

    private String initials(String fullName) {
        return java.util.Arrays.stream(fullName.split("\\s+"))
                .filter(part -> !part.isBlank())
                .limit(2)
                .map(part -> part.substring(0, 1).toUpperCase())
                .collect(java.util.stream.Collectors.joining());
    }

    private String cvMeta(long sizeBytes, boolean wasNull) {
        if (wasNull || sizeBytes <= 0) return "PDF oficial";
        return "PDF oficial · " + (sizeBytes < 1024 * 1024
                ? Math.max(1, Math.round(sizeBytes / 1024.0)) + " KB"
                : String.format(java.util.Locale.ROOT, "%.1f MB", sizeBytes / (1024.0 * 1024.0)));
    }

        public record ApplicantProfileCard(Long id, Long applicantId, String fullName, String email, String phone, String initials, String profileName,
            String categoryLabel, String location, List<String> sectors, String availability, String license,
            boolean hasVehicle, boolean hasApplication, boolean hasCv, String identityCard, Integer age,
            String experienceSummary, String latestJob, String cvFileName, String cvMeta, String fotoUrl) {}

        public record ProfileCv(Long id, String fileName, String meta, boolean active, java.time.OffsetDateTime uploadedAt) {}

    public record CvLink(String url) {}

    public record ContactApplication(Long ofertaId, String ofertaTitulo, Long perfilId, String perfilNombre) {}

    public record ContactNotificationRequest(@NotNull Boolean selected, @NotNull @Positive Long ofertaId) {}

    public record ContactNotification(String message) {}

    private record ContactRecipient(Long applicantUserId, String applicantName, String applicantEmail,
            String socioName, String socioEmail, String offerTitle) {}
}
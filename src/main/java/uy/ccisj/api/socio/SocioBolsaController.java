package uy.ccisj.api.socio;

import java.time.Duration;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
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
        private final int presignedMinutes;

    public SocioBolsaController(JdbcTemplate jdbcTemplate, UserRepository userRepository,
                        SocioRepository socioRepository, S3StorageService storageService,
                        @Value("${app.storage.presigned-minutes:15}") int presignedMinutes) {
        this.jdbcTemplate = jdbcTemplate;
        this.userRepository = userRepository;
        this.socioRepository = socioRepository;
        this.storageService = storageService;
        this.presignedMinutes = presignedMinutes;
    }

    @GetMapping
    public List<ApplicantProfileCard> search(Authentication authentication,
            @RequestParam(required = false) String rubro) {
        Long socioId = requireEligibleSocio(authentication.getName());
        return jdbcTemplate.query("""
                SELECT pl.id, p.nombre_completo, pl.nombre AS perfil_nombre, p.zona_residencia,
                       pl.disponibilidad_horaria, pl.libreta, pl.tiene_vehiculo,
                                                                                             COALESCE(string_agg(DISTINCT r.nombre_rubro, '|'), '') AS rubros,
                                                                                             CASE WHEN application.id IS NOT NULL THEN p.cedula_identidad END AS cedula_identidad,
                                                                                             CASE WHEN application.id IS NOT NULL
                                                                                                    THEN EXTRACT(YEAR FROM age(current_date, p.fecha_nacimiento))::integer END AS edad,
                                                                                             CASE WHEN application.id IS NOT NULL THEN pl.descripcion_experiencia END AS descripcion_experiencia,
                                                                                             CASE WHEN application.id IS NOT NULL THEN pl.ultimo_empleo END AS ultimo_empleo,
                                                                                             application.id IS NOT NULL AS tiene_postulacion,
                                                                                             cv.id IS NOT NULL AS tiene_cv,
                                                                                             cv.nombre_archivo, cv.size_bytes
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
                 WHERE pl.visible = TRUE AND p.visible = TRUE
                   AND (? IS NULL OR EXISTS (
                       SELECT 1 FROM perfil_rubros filter_pr
                       JOIN rubros_empleo filter_r ON filter_r.id = filter_pr.rubro_id
                       WHERE filter_pr.perfil_id = pl.id AND filter_r.nombre_rubro = ?))
                 GROUP BY p.id, p.nombre_completo, pl.id, pl.nombre, p.zona_residencia,
                          pl.disponibilidad_horaria, pl.libreta, pl.tiene_vehiculo,
                          p.cedula_identidad, p.fecha_nacimiento, pl.descripcion_experiencia,
                          pl.ultimo_empleo, application.id, cv.id, cv.nombre_archivo, cv.size_bytes
                 ORDER BY pl.id DESC
                """, (rs, rowNum) -> {
                    String fullName = rs.getString("nombre_completo");
                    List<String> sectors = splitRubros(rs.getString("rubros"));
                    String availability = rs.getString("disponibilidad_horaria");
                    return new ApplicantProfileCard(rs.getLong("id"), fullName, initials(fullName),
                            rs.getString("perfil_nombre"), sectors.isEmpty() ? "OTROS" : sectors.getFirst().toUpperCase(),
                            rs.getString("zona_residencia"), sectors, availability, rs.getString("libreta"),
                            rs.getBoolean("tiene_vehiculo"), rs.getBoolean("tiene_postulacion"),
                            rs.getBoolean("tiene_cv"),
                            rs.getString("cedula_identidad"), (Integer) rs.getObject("edad"),
                            rs.getString("descripcion_experiencia"), rs.getString("ultimo_empleo"),
                            rs.getString("nombre_archivo"), cvMeta(rs.getLong("size_bytes"), rs.wasNull()));
                }, socioId, rubro == null || rubro.isBlank() ? null : rubro.trim(),
                        rubro == null || rubro.isBlank() ? null : rubro.trim());
    }

        @GetMapping("/{profileId}/cv")
        public CvLink downloadCv(Authentication authentication, @PathVariable Long profileId) {
                Long socioId = requireEligibleSocio(authentication.getName());
                String key = jdbcTemplate.query("""
                                SELECT cv.ruta_archivo_cv
                                  FROM postulaciones a
                                  JOIN ofertas_empleo o ON o.id = a.oferta_id
                                  JOIN perfiles_laborales pl ON pl.id = a.perfil_laboral_id
                                  JOIN cvs cv ON cv.id = a.cv_id
                                 WHERE pl.id = ? AND o.socio_id = ?
                                 ORDER BY a.fecha DESC
                                 LIMIT 1
                                """, rs -> rs.next() ? rs.getString(1) : null, profileId, socioId);
                if (key == null || key.isBlank()) {
                        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No hay un CV disponible para este perfil");
                }
                return new CvLink(storageService.presignedDownloadUrl(key, Duration.ofMinutes(presignedMinutes)));
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

    public record ApplicantProfileCard(Long id, String fullName, String initials, String profileName,
            String categoryLabel, String location, List<String> sectors, String availability, String license,
            boolean hasVehicle, boolean hasApplication, boolean hasCv, String identityCard, Integer age,
            String experienceSummary, String latestJob, String cvFileName, String cvMeta) {}

    public record CvLink(String url) {}
}
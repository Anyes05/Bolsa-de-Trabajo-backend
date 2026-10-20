package uy.ccisj.api.postulante;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import uy.ccisj.api.storage.S3StorageService;
import uy.ccisj.api.user.Role;
import uy.ccisj.api.user.UserRepository;

@RestController
@RequestMapping("/admin/postulantes")
public class AdminPostulanteController {
    private final JdbcTemplate jdbcTemplate;
    private final UserRepository userRepository;
    private final S3StorageService storageService;
    private final int presignedMinutes;

    public AdminPostulanteController(JdbcTemplate jdbcTemplate, UserRepository userRepository,
            S3StorageService storageService, @Value("${app.storage.presigned-minutes:15}") int presignedMinutes) {
        this.jdbcTemplate = jdbcTemplate;
        this.userRepository = userRepository;
        this.storageService = storageService;
        this.presignedMinutes = presignedMinutes;
    }

    @GetMapping
    public List<ApplicantProfile> list(Authentication authentication) {
        requireAdmin(authentication.getName());
        return jdbcTemplate.query("""
                SELECT pl.id, p.id AS postulante_id, p.nombre_completo, u.email, pl.nombre AS perfil_nombre,
                       p.zona_residencia, pl.disponibilidad_horaria, pl.libreta, pl.tiene_vehiculo,
                       COALESCE(string_agg(DISTINCT r.nombre_rubro, '|'), '') AS rubros,
                       p.cedula_identidad, EXTRACT(YEAR FROM age(current_date, p.fecha_nacimiento))::integer AS edad,
                       pl.descripcion_experiencia, pl.ultimo_empleo, cv.id IS NOT NULL AS tiene_cv,
                       cv.nombre_archivo, cv.size_bytes
                  FROM perfiles_laborales pl
                  JOIN postulantes p ON p.id = pl.postulante_id
                  JOIN usuarios u ON u.id = p.usuario_id
                  LEFT JOIN perfil_rubros pr ON pr.perfil_id = pl.id
                  LEFT JOIN rubros_empleo r ON r.id = pr.rubro_id
                  LEFT JOIN LATERAL (
                      SELECT id, nombre_archivo, size_bytes
                        FROM cvs
                       WHERE perfil_laboral_id = pl.id
                       ORDER BY activo DESC, fecha_carga DESC, id DESC
                       LIMIT 1
                  ) cv ON TRUE
                 GROUP BY pl.id, p.id, p.nombre_completo, u.email, pl.nombre, p.zona_residencia,
                          pl.disponibilidad_horaria, pl.libreta, pl.tiene_vehiculo, p.cedula_identidad,
                          p.fecha_nacimiento, pl.descripcion_experiencia, pl.ultimo_empleo,
                          cv.id, cv.nombre_archivo, cv.size_bytes
                 ORDER BY p.nombre_completo, pl.id
                """, (rs, rowNum) -> {
            String fullName = rs.getString("nombre_completo");
            List<String> sectors = splitRubros(rs.getString("rubros"));
            return new ApplicantProfile(rs.getLong("id"), rs.getLong("postulante_id"), fullName,
                    rs.getString("email"), initials(fullName), rs.getString("perfil_nombre"),
                    sectors.isEmpty() ? "OTROS" : sectors.getFirst().toUpperCase(),
                    rs.getString("zona_residencia"), sectors, rs.getString("disponibilidad_horaria"),
                    rs.getString("libreta"), rs.getBoolean("tiene_vehiculo"), rs.getBoolean("tiene_cv"),
                    rs.getString("cedula_identidad"), (Integer) rs.getObject("edad"),
                    rs.getString("descripcion_experiencia"), rs.getString("ultimo_empleo"),
                    rs.getString("nombre_archivo"), cvMeta(rs.getLong("size_bytes"), rs.wasNull()));
        });
    }

    @GetMapping("/{profileId}/cvs")
    public List<ProfileCv> listCvs(Authentication authentication, @PathVariable Long profileId) {
        requireAdmin(authentication.getName());
        return jdbcTemplate.query("""
                SELECT id, nombre_archivo, size_bytes, activo, fecha_carga
                  FROM cvs
                 WHERE perfil_laboral_id = ?
                 ORDER BY activo DESC, fecha_carga DESC, id DESC
                """, (rs, rowNum) -> new ProfileCv(rs.getLong("id"), rs.getString("nombre_archivo"),
                        cvMeta(rs.getLong("size_bytes"), rs.wasNull()), rs.getBoolean("activo"),
                        rs.getObject("fecha_carga", OffsetDateTime.class)), profileId);
    }

    @GetMapping("/{profileId}/cvs/{cvId}")
    public CvLink downloadCv(Authentication authentication, @PathVariable Long profileId, @PathVariable Long cvId) {
        requireAdmin(authentication.getName());
        String key = jdbcTemplate.query("""
                SELECT ruta_archivo_cv FROM cvs WHERE id = ? AND perfil_laboral_id = ?
                """, rs -> rs.next() ? rs.getString(1) : null, cvId, profileId);
        if (key == null || key.isBlank()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "CV no encontrado para este perfil");
        }
        return new CvLink(storageService.presignedDownloadUrl(key, Duration.ofMinutes(presignedMinutes)));
    }

    private void requireAdmin(String email) {
        boolean isAdmin = userRepository.findByEmailIgnoreCase(email)
                .filter(user -> user.isActivo() && user.getRole() == Role.ADMIN)
                .isPresent();
        if (!isAdmin) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Se requiere una cuenta de administrador activa");
        }
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

    public record ApplicantProfile(Long id, Long applicantId, String fullName, String email, String initials,
            String profileName, String categoryLabel, String location, List<String> sectors, String availability,
            String license, boolean hasVehicle, boolean hasCv, String identityCard, Integer age,
            String experienceSummary, String latestJob, String cvFileName, String cvMeta) {}

    public record ProfileCv(Long id, String fileName, String meta, boolean active, OffsetDateTime uploadedAt) {}

    public record CvLink(String url) {}
}
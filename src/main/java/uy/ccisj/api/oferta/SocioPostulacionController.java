package uy.ccisj.api.oferta;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.transaction.annotation.Transactional;
import uy.ccisj.api.storage.S3StorageService;
import uy.ccisj.api.socio.SocioRepository;
import uy.ccisj.api.user.Role;
import uy.ccisj.api.user.UserRepository;

@RestController
@RequestMapping("/socio")
public class SocioPostulacionController {
    private static final DateTimeFormatter APPLICATION_DATE_FORMAT = DateTimeFormatter.ofPattern("d/M/uuuu");
    private final JdbcTemplate jdbcTemplate;
    private final PostulacionRepository postulacionRepository;
    private final UserRepository userRepository;
    private final SocioRepository socioRepository;
    private final S3StorageService storageService;
    private final int presignedMinutes;

    public SocioPostulacionController(JdbcTemplate jdbcTemplate, PostulacionRepository postulacionRepository,
            UserRepository userRepository, SocioRepository socioRepository, S3StorageService storageService,
            @Value("${app.storage.presigned-minutes:15}") int presignedMinutes) {
        this.jdbcTemplate = jdbcTemplate;
        this.postulacionRepository = postulacionRepository;
        this.userRepository = userRepository;
        this.socioRepository = socioRepository;
        this.storageService = storageService;
        this.presignedMinutes = presignedMinutes;
    }

    @GetMapping("/ofertas/{ofertaId}/postulaciones")
    public List<ReceivedApplication> list(Authentication authentication, @PathVariable Long ofertaId) {
        Long socioId = requireEligibleSocio(authentication.getName());
        return jdbcTemplate.query("""
                SELECT a.id, a.estado, a.fecha, pl.id AS perfil_id, po.nombre_completo,
                       po.cedula_identidad, po.zona_residencia, pl.nombre AS perfil_nombre,
                       pl.disponibilidad_horaria, pl.tiene_vehiculo, pl.libreta,
                       pl.ultimo_empleo, pl.descripcion_experiencia,
                       COALESCE(string_agg(DISTINCT r.nombre_rubro, '|'), '') AS rubros,
                       cv.id AS cv_id, cv.nombre_archivo, cv.size_bytes
                  FROM postulaciones a
                  JOIN ofertas_empleo o ON o.id = a.oferta_id AND o.socio_id = ?
                  JOIN postulantes po ON po.id = a.postulante_id
                  JOIN perfiles_laborales pl ON pl.id = a.perfil_laboral_id
                  LEFT JOIN perfil_rubros pr ON pr.perfil_id = pl.id
                  LEFT JOIN rubros_empleo r ON r.id = pr.rubro_id
                  LEFT JOIN cvs cv ON cv.id = a.cv_id
                 WHERE a.oferta_id = ?
                 GROUP BY a.id, a.estado, a.fecha, pl.id, po.nombre_completo,
                          po.cedula_identidad, po.zona_residencia, pl.id, pl.nombre,
                          pl.disponibilidad_horaria, pl.tiene_vehiculo, pl.libreta,
                          pl.ultimo_empleo, pl.descripcion_experiencia, cv.id,
                          cv.nombre_archivo, cv.size_bytes
                 ORDER BY a.fecha DESC
                """, (rs, rowNum) -> new ReceivedApplication(rs.getLong("id"),
                rs.getString("nombre_completo"), initials(rs.getString("nombre_completo")),
                rs.getLong("perfil_id"), rs.getString("perfil_nombre"), rs.getString("zona_residencia"),
                splitRubros(rs.getString("rubros")), rs.getString("estado"),
                formatDate(rs.getObject("fecha", OffsetDateTime.class)),
                rs.getString("disponibilidad_horaria"), rs.getBoolean("tiene_vehiculo"),
                rs.getString("libreta"), rs.getString("ultimo_empleo"), rs.getString("descripcion_experiencia"),
                    rs.getString("cedula_identidad"), rs.getObject("cv_id", Long.class), rs.getString("nombre_archivo"),
                cvMeta(rs.getLong("size_bytes"), rs.wasNull())), socioId, ofertaId);
    }

    @PatchMapping("/postulaciones/{postulacionId}/estado")
    @Transactional
    public ReceivedStatus updateStatus(Authentication authentication, @PathVariable Long postulacionId,
            @RequestParam EstadoPostulacion estado) {
        Long socioId = requireEligibleSocio(authentication.getName());
        Postulacion application = postulacionRepository.findByIdAndOfertaSocioId(postulacionId, socioId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Postulación no encontrada"));
        if (!canTransition(application.getEstado(), estado)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "El estado solicitado no es una transición válida");
        }
        application.setEstado(estado);
        return new ReceivedStatus(application.getId(), application.getEstado());
    }

    @GetMapping("/postulaciones/{postulacionId}/cv")
    public CvLink downloadCv(Authentication authentication, @PathVariable Long postulacionId) {
        Long socioId = requireEligibleSocio(authentication.getName());
        Postulacion application = postulacionRepository.findByIdAndOfertaSocioId(postulacionId, socioId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Postulación no encontrada"));
        if (application.getCv() == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "La postulación no tiene un CV asociado");
        }
        String key = application.getCv().getRutaArchivoCv();
        if (key == null || key.isBlank()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "El CV no tiene un archivo disponible");
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

    private boolean canTransition(EstadoPostulacion current, EstadoPostulacion next) {
        if (current == next) return true;
        return switch (current) {
            case RECIBIDA -> next == EstadoPostulacion.REVISADA || next == EstadoPostulacion.DESCARTADA;
            case REVISADA -> next == EstadoPostulacion.CONTACTADA || next == EstadoPostulacion.SELECCIONADA
                    || next == EstadoPostulacion.DESCARTADA;
            case CONTACTADA -> next == EstadoPostulacion.SELECCIONADA || next == EstadoPostulacion.DESCARTADA;
            case SELECCIONADA, DESCARTADA -> false;
        };
    }

    private List<String> splitRubros(String value) {
        if (value == null || value.isBlank()) return List.of();
        return java.util.Arrays.stream(value.split("\\|"))
                .filter(item -> !item.isBlank()).toList();
    }

    private String initials(String fullName) {
        return java.util.Arrays.stream(fullName.split("\\s+"))
                .filter(part -> !part.isBlank()).limit(2)
                .map(part -> part.substring(0, 1).toUpperCase())
                .collect(java.util.stream.Collectors.joining());
    }

    private String cvMeta(long sizeBytes, boolean wasNull) {
        if (wasNull || sizeBytes <= 0) return "PDF oficial";
        return "PDF oficial · " + Math.max(1, Math.round(sizeBytes / 1024.0)) + " KB";
    }

    private String formatDate(OffsetDateTime date) {
        return date.format(APPLICATION_DATE_FORMAT);
    }

    public record ReceivedApplication(Long id, String fullName, String initials, Long profileId,
            String profileName, String location, List<String> tags, String status, String appliedOn,
            String availability, boolean hasVehicle, String license, String latestJob, String experienceSummary,
            String identityCard, Long cvId, String cvFileName, String cvMeta) {}

    public record ReceivedStatus(Long id, EstadoPostulacion status) {}
    public record CvLink(String url) {}
}
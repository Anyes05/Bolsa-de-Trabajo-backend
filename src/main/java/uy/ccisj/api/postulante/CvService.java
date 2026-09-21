package uy.ccisj.api.postulante;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import uy.ccisj.api.storage.S3StorageService;
import uy.ccisj.api.user.UserRepository;

@Service
public class CvService {
    private static final Logger LOGGER = LoggerFactory.getLogger(CvService.class);
    private static final Set<String> ALLOWED_FALLBACK = Set.of(
            "application/pdf",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document");

    private final PostulanteRepository postulanteRepository;
    private final UserRepository userRepository;
    private final CvRepository cvRepository;
    private final S3StorageService storageService;
    private final JdbcTemplate jdbcTemplate;
    private final int maxFileSizeMb;
    private final int maxCvsPerPostulante;
    private final int presignedMinutes;
    private final Set<String> allowedMime;

    public CvService(
            PostulanteRepository postulanteRepository,
            UserRepository userRepository,
            CvRepository cvRepository,
            S3StorageService storageService,
            JdbcTemplate jdbcTemplate,
            @Value("${app.storage.max-file-size-mb:10}") int maxFileSizeMb,
            @Value("${app.storage.max-cvs-per-postulante:5}") int maxCvsPerPostulante,
            @Value("${app.storage.presigned-minutes:15}") int presignedMinutes,
            @Value("${app.storage.allowed-mime:}") String allowedMimeRaw) {
        this.postulanteRepository = postulanteRepository;
        this.userRepository = userRepository;
        this.cvRepository = cvRepository;
        this.storageService = storageService;
        this.jdbcTemplate = jdbcTemplate;
        this.maxFileSizeMb = maxFileSizeMb;
        this.maxCvsPerPostulante = maxCvsPerPostulante;
        this.presignedMinutes = presignedMinutes;
        this.allowedMime = parseAllowedMime(allowedMimeRaw);
    }

    @Transactional(readOnly = true)
    public List<CvResponse> listMyCvs(String userEmail) {
        var postulante = requirePostulanteByEmail(userEmail);
        return cvRepository.findByPostulanteIdOrderByVersionDesc(postulante.getId()).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public CvResponse uploadCv(String userEmail, MultipartFile file, String resumen) {
        var postulante = requirePostulanteByEmail(userEmail);

        if (cvRepository.countByPostulanteId(postulante.getId()) >= maxCvsPerPostulante) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Alcanzaste el maximo de CVs permitidos por postulante");
        }

        validateFile(file);

        var allCvs = cvRepository.findByPostulanteIdOrderByVersionDesc(postulante.getId());
        int nextVersion = allCvs.isEmpty() ? 1 : allCvs.getFirst().getVersion() + 1;
        allCvs.forEach(cv -> cv.setActivo(false));

        var cv = new Cv(postulante);
        cv.setResumen(resumen);
        cv.setVersion(nextVersion);
        cv.setActivo(true);
        cv.setNombreArchivo(file.getOriginalFilename());
        cv.setMimeType(file.getContentType());
        cv.setSizeBytes(file.getSize());
        cv.setFechaCarga(OffsetDateTime.now());

        String extension = extensionOf(file.getOriginalFilename());
        String key = "postulantes/" + postulante.getId() + "/cvs/" + UUID.randomUUID() + extension;
        cv.setRutaArchivoCv(key);

        var saved = cvRepository.save(cv);

        try {
            storageService.putObject(key, file);
            return toResponse(saved);
        } catch (RuntimeException exception) {
            cvRepository.delete(saved);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "No se pudo almacenar el archivo en S3", exception);
        }
    }

    @Transactional
    public CvResponse activateCv(String userEmail, Long cvId) {
        var postulante = requirePostulanteByEmail(userEmail);
        var target = cvRepository.findByIdAndPostulanteId(cvId, postulante.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "CV no encontrado"));

        var allCvs = cvRepository.findByPostulanteIdOrderByVersionDesc(postulante.getId());
        allCvs.forEach(cv -> cv.setActivo(cv.getId().equals(target.getId())));

        return toResponse(target);
    }

    @Transactional
    public void deleteCv(String userEmail, Long cvId) {
        var postulante = requirePostulanteByEmail(userEmail);
        var cv = cvRepository.findByIdAndPostulanteId(cvId, postulante.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "CV no encontrado"));

        try {
            storageService.deleteObject(cv.getRutaArchivoCv());
        } catch (RuntimeException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "No se pudo eliminar el archivo en S3", exception);
        }

        boolean wasActive = cv.isActivo();
        cvRepository.delete(cv);

        if (wasActive) {
            cvRepository.findByPostulanteIdOrderByVersionDesc(postulante.getId()).stream()
                    .findFirst()
                    .ifPresent(next -> next.setActivo(true));
        }
    }

    @Transactional(readOnly = true)
    public CvDownloadResponse downloadLink(String userEmail, Long cvId) {
        var postulante = requirePostulanteByEmail(userEmail);
        var cv = cvRepository.findByIdAndPostulanteId(cvId, postulante.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "CV no encontrado"));
        String url = storageService.presignedDownloadUrl(cv.getRutaArchivoCv(), Duration.ofMinutes(presignedMinutes));
        return new CvDownloadResponse(url);
    }

    private Postulante requirePostulanteByEmail(String email) {
        var user = userRepository.findByEmailIgnoreCase(email)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN,
                "La cuenta autenticada no tiene un perfil de postulante"));

        var byUserId = postulanteRepository.findByUsuarioId(user.getId());
        if (byUserId.isPresent()) {
            return byUserId.get();
        }

        var byEmail = postulanteRepository.findByUsuarioEmailIgnoreCase(email);
        if (byEmail.isPresent()) {
            return byEmail.get();
        }

        // Fallback defensivo para entornos con diferencias de mapeo/JPA en runtime.
        Long postulanteId = jdbcTemplate.query(
            """
            SELECT p.id
              FROM postulantes p
              JOIN usuarios u ON u.id = p.usuario_id
             WHERE u.id = ?
             LIMIT 1
            """,
            rs -> rs.next() ? rs.getLong(1) : null,
            user.getId());

        if (postulanteId != null) {
            return postulanteRepository.findById(postulanteId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "La cuenta autenticada no tiene un perfil de postulante"));
        }

        LOGGER.warn("No se pudo resolver postulante para email={} userId={} role={}",
            email, user.getId(), user.getRole());

        throw new ResponseStatusException(HttpStatus.FORBIDDEN,
            "La cuenta autenticada no tiene un perfil de postulante");
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Debe adjuntar un archivo de CV");
        }

        long maxBytes = (long) maxFileSizeMb * 1024 * 1024;
        if (file.getSize() > maxBytes) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "El archivo supera el tamano maximo permitido de " + maxFileSizeMb + " MB");
        }

        String contentType = file.getContentType();
        if (contentType == null || !allowedMime.contains(contentType.toLowerCase(Locale.ROOT))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Tipo de archivo no permitido. Solo PDF o DOCX");
        }
    }

    private Set<String> parseAllowedMime(String raw) {
        if (raw == null || raw.isBlank()) {
            return ALLOWED_FALLBACK;
        }
        var parsed = List.of(raw.split(",")).stream()
                .map(value -> value.trim().toLowerCase(Locale.ROOT))
                .filter(value -> !value.isBlank())
                .collect(Collectors.toSet());
        return parsed.isEmpty() ? ALLOWED_FALLBACK : parsed;
    }

    private String extensionOf(String filename) {
        if (filename == null || !filename.contains(".")) {
            return "";
        }
        String extension = filename.substring(filename.lastIndexOf('.'));
        if (extension.length() > 10) {
            return "";
        }
        return extension.toLowerCase(Locale.ROOT);
    }

    private CvResponse toResponse(Cv cv) {
        String downloadUrl = storageService.presignedDownloadUrl(cv.getRutaArchivoCv(), Duration.ofMinutes(presignedMinutes));
        return new CvResponse(
                cv.getId(),
                cv.getVersion(),
                cv.getResumen(),
                cv.getNombreArchivo(),
                cv.getMimeType(),
                cv.getSizeBytes(),
                cv.isActivo(),
                cv.getFechaCarga(),
                downloadUrl);
    }
}

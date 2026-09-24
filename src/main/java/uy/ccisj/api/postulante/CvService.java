package uy.ccisj.api.postulante;

import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import uy.ccisj.api.domain.EstadoCivil;
import uy.ccisj.api.domain.Genero;
import uy.ccisj.api.storage.S3StorageService;
import uy.ccisj.api.user.UserRepository;

@Service
public class CvService {
    private static final Logger LOGGER = LoggerFactory.getLogger(CvService.class);
    private static final DateTimeFormatter[] DATE_FORMATS = new DateTimeFormatter[] {
        DateTimeFormatter.ofPattern("d/M/uuuu"),
        DateTimeFormatter.ofPattern("d-M-uuuu"),
        DateTimeFormatter.ofPattern("d.M.uuuu")
    };
    private static final Pattern BIRTH_DATE_PATTERN = Pattern.compile(
        "(?i)(?:fecha\\s*de\\s*nacimiento|nacimiento|f\\.?\\s*nac\\.?)[^0-9]{0,20}(\\d{1,2}[\\/\\-.]\\d{1,2}[\\/\\-.]\\d{4})");
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
    public CvResponse uploadCv(String userEmail, MultipartFile file, String resumen, Long perfilLaboralId) {
        var postulante = requirePostulanteByEmail(userEmail);

        if (cvRepository.countByPostulanteId(postulante.getId()) >= maxCvsPerPostulante) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Alcanzaste el maximo de CVs permitidos por postulante");
        }

        validateFile(file);
        enrichPostulanteFromPdfIfMissing(postulante, file);

        var allCvs = cvRepository.findByPostulanteIdOrderByVersionDesc(postulante.getId());
        int nextVersion = allCvs.isEmpty() ? 1 : allCvs.getFirst().getVersion() + 1;
        allCvs.forEach(cv -> cv.setActivo(false));
        // La base admite una sola version activa: confirma el cambio antes del nuevo INSERT.
        cvRepository.flush();

        var cv = new Cv(postulante);
        cv.setPerfilLaboral(resolvePerfil(postulante.getId(), perfilLaboralId));
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
            LOGGER.error(
                    "Fallo upload CV a S3 postulanteId={} cvId={} key={} fileName={} contentType={} size={}",
                    postulante.getId(),
                    saved.getId(),
                    key,
                    file.getOriginalFilename(),
                    file.getContentType(),
                    file.getSize(),
                    exception);
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

        private PerfilLaboral resolvePerfil(Long postulanteId, Long perfilLaboralId) {
        Long resolvedId = perfilLaboralId != null ? perfilLaboralId : jdbcTemplate.query(
            "SELECT id FROM perfiles_laborales WHERE postulante_id = ? ORDER BY id LIMIT 1",
            rs -> rs.next() ? rs.getLong(1) : null,
            postulanteId);
        if (resolvedId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Debes crear un perfil profesional antes de subir un CV");
        }
        Integer matches = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM perfiles_laborales WHERE id = ? AND postulante_id = ?",
            Integer.class, resolvedId, postulanteId);
        if (matches == null || matches == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Perfil profesional no encontrado");
        }
        return jdbcTemplate.query(
            "SELECT id FROM perfiles_laborales WHERE id = ?",
            rs -> rs.next() ? postulanteRepository.findById(postulanteId)
                .flatMap(postulante -> postulante.getPerfiles().stream().filter(perfil -> perfil.getId().equals(resolvedId)).findFirst())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Perfil profesional no encontrado")) : null,
            resolvedId);
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

    private void enrichPostulanteFromPdfIfMissing(Postulante postulante, MultipartFile file) {
        if (postulante.getFechaNacimiento() != null
                && postulante.getGenero() != null
                && postulante.getEstadoCivil() != null) {
            return;
        }

        String contentType = file.getContentType();
        if (contentType == null || !contentType.equalsIgnoreCase("application/pdf")) {
            return;
        }

        try {
            String text = extractPdfText(file);

            if (postulante.getFechaNacimiento() == null) {
                extractBirthDate(text).ifPresent(postulante::setFechaNacimiento);
            }
            if (postulante.getGenero() == null) {
                extractGenero(text).ifPresent(postulante::setGenero);
            }
            if (postulante.getEstadoCivil() == null) {
                extractEstadoCivil(text).ifPresent(postulante::setEstadoCivil);
            }
        } catch (Exception exception) {
            // No bloquea el alta/carga: estos datos son opcionales y pueden quedar en null.
            LOGGER.info("No se pudieron extraer campos opcionales desde PDF para postulanteId={}", postulante.getId());
        }
    }

    private String extractPdfText(MultipartFile file) throws Exception {
        try (PDDocument document = Loader.loadPDF(file.getBytes())) {
            PDFTextStripper stripper = new PDFTextStripper();
            return stripper.getText(document);
        }
    }

    private Optional<LocalDate> extractBirthDate(String text) {
        Matcher matcher = BIRTH_DATE_PATTERN.matcher(text);
        if (!matcher.find()) {
            return Optional.empty();
        }
        String value = matcher.group(1).trim();
        for (DateTimeFormatter formatter : DATE_FORMATS) {
            try {
                return Optional.of(LocalDate.parse(value, formatter));
            } catch (DateTimeParseException ignored) {
                // Intenta el siguiente formato.
            }
        }
        return Optional.empty();
    }

    private Optional<Genero> extractGenero(String text) {
        String normalized = normalize(text);
        if (normalized.contains("prefiero no especificar")
                || normalized.contains("no especifica")) {
            return Optional.of(Genero.PREFIERO_NO_ESPECIFICAR);
        }
        if (normalized.contains("femenino") || normalized.contains("mujer")) {
            return Optional.of(Genero.FEMENINO);
        }
        if (normalized.contains("masculino") || normalized.contains("hombre")) {
            return Optional.of(Genero.MASCULINO);
        }
        return Optional.empty();
    }

    private Optional<EstadoCivil> extractEstadoCivil(String text) {
        String normalized = normalize(text);
        if (normalized.contains("union libre")) {
            return Optional.of(EstadoCivil.UNION_LIBRE);
        }
        if (normalized.contains("divorciado") || normalized.contains("divorciada")) {
            return Optional.of(EstadoCivil.DIVORCIADO);
        }
        if (normalized.contains("viudo") || normalized.contains("viuda")) {
            return Optional.of(EstadoCivil.VIUDO);
        }
        if (normalized.contains("casado") || normalized.contains("casada")) {
            return Optional.of(EstadoCivil.CASADO);
        }
        if (normalized.contains("soltero") || normalized.contains("soltera")) {
            return Optional.of(EstadoCivil.SOLTERO);
        }
        return Optional.empty();
    }

    private String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
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
            cv.getPerfilLaboral() == null ? null : cv.getPerfilLaboral().getId(),
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

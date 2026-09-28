package uy.ccisj.api.postulante;

import java.util.Base64;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/postulante/datos-personales")
public class PostulanteDatosPersonalesController {
    private final JdbcTemplate jdbcTemplate;

    public PostulanteDatosPersonalesController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping
    public DatosPersonalesResponse get(Authentication authentication) {
        return find(authentication.getName());
    }

    @PutMapping
    public DatosPersonalesResponse update(Authentication authentication, @RequestBody DatosPersonalesRequest request) {
        if (request.nombreCompleto() == null || request.nombreCompleto().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El nombre completo es obligatorio");
        }
        int updated = jdbcTemplate.update("""
                UPDATE postulantes p SET nombre_completo = ?, cedula_identidad = ?, telefono = ?, zona_residencia = ?
                FROM usuarios u WHERE p.usuario_id = u.id AND u.email = ?
                """, request.nombreCompleto().trim(), blankToNull(request.cedulaIdentidad()),
                blankToNull(request.telefono()), blankToNull(request.zonaResidencia()), authentication.getName());
        if (updated == 0) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Postulante no encontrado");
        return find(authentication.getName());
    }

    @PostMapping("/foto")
    @ResponseStatus(HttpStatus.CREATED)
    public DatosPersonalesResponse uploadPhoto(Authentication authentication, @RequestParam("file") MultipartFile file) {
        String type = file.getContentType() == null ? "" : file.getContentType().toLowerCase(Locale.ROOT);
        if (file.isEmpty() || !type.startsWith("image/") || file.getSize() > 5 * 1024 * 1024) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La foto debe ser una imagen de hasta 5 MB");
        }
        Long postulanteId = jdbcTemplate.query("""
                SELECT p.id FROM postulantes p JOIN usuarios u ON u.id = p.usuario_id WHERE u.email = ?
                """, rs -> rs.next() ? rs.getLong(1) : null, authentication.getName());
        if (postulanteId == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Postulante no encontrado");
        jdbcTemplate.update("UPDATE postulantes SET foto_perfil = ?, foto_mime = ? WHERE id = ?",
            file.getBytes(), type, postulanteId);
        return find(authentication.getName());
    }

    private DatosPersonalesResponse find(String email) {
        return jdbcTemplate.query("""
                  SELECT p.nombre_completo, p.cedula_identidad, p.telefono, p.zona_residencia,
                      p.foto_perfil, p.foto_mime, u.email
                FROM postulantes p JOIN usuarios u ON u.id = p.usuario_id WHERE u.email = ?
                """, rs -> rs.next() ? new DatosPersonalesResponse(rs.getString("nombre_completo"),
                rs.getString("cedula_identidad"), rs.getString("telefono"), rs.getString("zona_residencia"),
                rs.getString("email"), photoUrl(rs.getBytes("foto_perfil"), rs.getString("foto_mime"))) : null, email);
    }

    private String photoUrl(byte[] bytes, String mime) {
        return bytes == null ? null : "data:" + mime + ";base64," + Base64.getEncoder().encodeToString(bytes);
    }
    private String blankToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }

    public record DatosPersonalesRequest(String nombreCompleto, String cedulaIdentidad, String telefono, String zonaResidencia) {}
    public record DatosPersonalesResponse(String nombreCompleto, String cedulaIdentidad, String telefono,
            String zonaResidencia, String email, String fotoUrl) {}
}
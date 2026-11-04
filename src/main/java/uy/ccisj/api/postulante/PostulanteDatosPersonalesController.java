package uy.ccisj.api.postulante;

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
import uy.ccisj.api.auth.ProfilePhotoService;

@RestController
@RequestMapping("/postulante/datos-personales")
public class PostulanteDatosPersonalesController {
    private final JdbcTemplate jdbcTemplate;
    private final ProfilePhotoService photos;

    public PostulanteDatosPersonalesController(JdbcTemplate jdbcTemplate, ProfilePhotoService photos) {
        this.jdbcTemplate = jdbcTemplate;
        this.photos = photos;
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
        Long postulanteId = jdbcTemplate.query("""
                SELECT p.id FROM postulantes p JOIN usuarios u ON u.id = p.usuario_id WHERE u.email = ?
                """, rs -> rs.next() ? rs.getLong(1) : null, authentication.getName());
        if (postulanteId == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Postulante no encontrado");
        photos.upload(authentication.getName(), file);
        return find(authentication.getName());
    }

    private DatosPersonalesResponse find(String email) {
        return jdbcTemplate.query("""
                  SELECT p.nombre_completo, p.cedula_identidad, p.telefono, p.zona_residencia,
                      u.foto_perfil, u.foto_mime, u.google_photo_url, u.email
                FROM postulantes p JOIN usuarios u ON u.id = p.usuario_id WHERE u.email = ?
                """, rs -> rs.next() ? new DatosPersonalesResponse(rs.getString("nombre_completo"),
                rs.getString("cedula_identidad"), rs.getString("telefono"), rs.getString("zona_residencia"),
                rs.getString("email"), ProfilePhotoService.photoUrl(rs.getBytes("foto_perfil"), rs.getString("foto_mime"),
                        rs.getString("google_photo_url"))) : null, email);
    }

    private String blankToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }

    public record DatosPersonalesRequest(String nombreCompleto, String cedulaIdentidad, String telefono, String zonaResidencia) {}
    public record DatosPersonalesResponse(String nombreCompleto, String cedulaIdentidad, String telefono,
            String zonaResidencia, String email, String fotoUrl) {}
}
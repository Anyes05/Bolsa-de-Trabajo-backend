package uy.ccisj.api.auth;

import java.io.IOException;
import java.util.Base64;
import java.util.Locale;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ProfilePhotoService {
    private static final Set<String> ALLOWED_TYPES = Set.of("image/jpeg", "image/png", "image/webp");
    private final JdbcTemplate jdbcTemplate;

    public ProfilePhotoService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public String find(String email) {
        return jdbcTemplate.query("SELECT foto_perfil, foto_mime, google_photo_url FROM usuarios WHERE email = ? AND activo = TRUE",
                rs -> {
                    if (!rs.next()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuario no encontrado");
                    return photoUrl(rs.getBytes("foto_perfil"), rs.getString("foto_mime"), rs.getString("google_photo_url"));
                }, email);
    }

    public String upload(String email, MultipartFile file) {
        String type = file.getContentType() == null ? "" : file.getContentType().toLowerCase(Locale.ROOT);
        if (file.isEmpty() || !ALLOWED_TYPES.contains(type) || file.getSize() > 5 * 1024 * 1024) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La foto debe ser JPG, PNG o WEBP de hasta 5 MB");
        }
        byte[] photo;
        try {
            photo = file.getBytes();
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No se pudo procesar la foto", exception);
        }
        requireUpdated(jdbcTemplate.update(
                "UPDATE usuarios SET foto_perfil = ?, foto_mime = ? WHERE email = ? AND activo = TRUE",
                photo, type, email));
        return photoUrl(photo, type);
    }

    public void remove(String email) {
        requireUpdated(jdbcTemplate.update(
                "UPDATE usuarios SET foto_perfil = NULL, foto_mime = NULL, google_photo_url = NULL WHERE email = ? AND activo = TRUE", email));
    }

    private void requireUpdated(int updated) {
        if (updated == 0) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuario no encontrado");
    }

    public static String photoUrl(byte[] bytes, String mime) {
        return bytes == null ? null : "data:" + mime + ";base64," + Base64.getEncoder().encodeToString(bytes);
    }

    public static String photoUrl(byte[] bytes, String mime, String googlePhotoUrl) {
        return bytes == null ? googlePhotoUrl : photoUrl(bytes, mime);
    }
}

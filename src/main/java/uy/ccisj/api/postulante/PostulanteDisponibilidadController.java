package uy.ccisj.api.postulante;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/postulante/disponibilidad-laboral")
public class PostulanteDisponibilidadController {
    private final JdbcTemplate jdbc;

    public PostulanteDisponibilidadController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping
    public Disponibilidad estado(Authentication authentication) {
        return jdbc.query("""
                SELECT p.busqueda_pausada FROM postulantes p
                JOIN usuarios u ON u.id = p.usuario_id
                WHERE lower(u.email) = lower(?) AND u.rol = 'POSTULANTE' AND u.activo = TRUE
                """, (rs, row) -> new Disponibilidad(rs.getBoolean("busqueda_pausada")),
                authentication.getName()).stream().findFirst().orElseThrow(this::notFound);
    }

    @PutMapping
    public Disponibilidad actualizar(Authentication authentication, @Valid @RequestBody Solicitud request) {
        return jdbc.query("""
                UPDATE postulantes p SET busqueda_pausada = ?
                FROM usuarios u
                WHERE u.id = p.usuario_id AND lower(u.email) = lower(?)
                  AND u.rol = 'POSTULANTE' AND u.activo = TRUE
                RETURNING p.busqueda_pausada
                """, (rs, row) -> new Disponibilidad(rs.getBoolean("busqueda_pausada")),
                request.busquedaPausada(), authentication.getName())
                .stream().findFirst().orElseThrow(this::notFound);
    }

    private ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Postulante activo no encontrado");
    }

    public record Disponibilidad(boolean busquedaPausada) {}
    public record Solicitud(@NotNull(message = "Indica si deseas pausar la búsqueda") Boolean busquedaPausada) {}
}

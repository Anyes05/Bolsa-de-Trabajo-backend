package uy.ccisj.api.postulante;

import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/postulante/perfiles")
public class PostulantePerfilController {
    private final JdbcTemplate jdbcTemplate;
    private final PostulanteRepository postulanteRepository;

    public PostulantePerfilController(JdbcTemplate jdbcTemplate, PostulanteRepository postulanteRepository) {
        this.jdbcTemplate = jdbcTemplate;
        this.postulanteRepository = postulanteRepository;
    }

    @GetMapping
    public List<PerfilResponse> list(Authentication authentication) {
        Long postulanteId = postulanteId(authentication.getName());
        return jdbcTemplate.query("""
                SELECT id, nombre, disponibilidad_horaria, tiene_vehiculo, ultimo_empleo,
                       descripcion_experiencia, visible
                  FROM perfiles_laborales
                 WHERE postulante_id = ?
                 ORDER BY id
                """, (rs, rowNum) -> new PerfilResponse(
                rs.getLong("id"), rs.getString("nombre"), rs.getString("disponibilidad_horaria"),
                rs.getBoolean("tiene_vehiculo"), rs.getString("ultimo_empleo"),
                rs.getString("descripcion_experiencia"), rs.getBoolean("visible"),
                rubros(rs.getLong("id"))), postulanteId);
    }

    @PutMapping("/{perfilId}")
    @Transactional
    public PerfilResponse update(Authentication authentication, @PathVariable Long perfilId,
            @RequestBody PerfilRequest request) {
        Long postulanteId = postulanteId(authentication.getName());
        if (request.nombre() == null || request.nombre().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El nombre del perfil es obligatorio");
        }
        int updated = jdbcTemplate.update("""
                UPDATE perfiles_laborales
                   SET nombre = ?, disponibilidad_horaria = ?, tiene_vehiculo = ?, ultimo_empleo = ?,
                       descripcion_experiencia = ?, visible = ?
                 WHERE id = ? AND postulante_id = ?
                """, request.nombre().trim(), request.disponibilidadHoraria(), request.tieneVehiculo(),
                nullIfBlank(request.ultimoEmpleo()), nullIfBlank(request.descripcionExperiencia()),
                request.visible(), perfilId, postulanteId);
        if (updated == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Perfil profesional no encontrado");
        }

        jdbcTemplate.update("DELETE FROM perfil_rubros WHERE perfil_id = ?", perfilId);
        for (String rubro : request.rubros() == null ? List.<String>of() : request.rubros()) {
            int inserted = jdbcTemplate.update("""
                    INSERT INTO perfil_rubros (perfil_id, rubro_id)
                    SELECT ?, id FROM rubros_empleo WHERE activo = TRUE AND nombre_rubro = ?
                    ON CONFLICT DO NOTHING
                    """, perfilId, rubro);
            if (inserted == 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Rubro no valido: " + rubro);
            }
        }
        return find(perfilId, postulanteId);
    }

    private PerfilResponse find(Long perfilId, Long postulanteId) {
        return jdbcTemplate.query("""
                SELECT id, nombre, disponibilidad_horaria, tiene_vehiculo, ultimo_empleo,
                       descripcion_experiencia, visible
                  FROM perfiles_laborales
                 WHERE id = ? AND postulante_id = ?
                """, rs -> rs.next() ? new PerfilResponse(
                rs.getLong("id"), rs.getString("nombre"), rs.getString("disponibilidad_horaria"),
                rs.getBoolean("tiene_vehiculo"), rs.getString("ultimo_empleo"),
                rs.getString("descripcion_experiencia"), rs.getBoolean("visible"), rubros(rs.getLong("id"))) : null,
                perfilId, postulanteId);
    }

    private List<String> rubros(Long perfilId) {
        return jdbcTemplate.queryForList("""
                SELECT r.nombre_rubro FROM perfil_rubros pr
                JOIN rubros_empleo r ON r.id = pr.rubro_id
                WHERE pr.perfil_id = ? ORDER BY r.nombre_rubro
                """, String.class, perfilId);
    }

    private Long postulanteId(String email) {
        return postulanteRepository.findByUsuarioEmailIgnoreCase(email)
                .map(Postulante::getId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Perfil de postulante no encontrado"));
    }

    private String nullIfBlank(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public record PerfilRequest(String nombre, String disponibilidadHoraria, boolean tieneVehiculo,
            String ultimoEmpleo, String descripcionExperiencia, boolean visible, List<String> rubros) {}

    public record PerfilResponse(Long id, String nombre, String disponibilidadHoraria, boolean tieneVehiculo,
            String ultimoEmpleo, String descripcionExperiencia, boolean visible, List<String> rubros) {}
}
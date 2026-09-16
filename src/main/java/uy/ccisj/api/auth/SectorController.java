package uy.ccisj.api.auth;

import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/sectors")
public class SectorController {
    private final JdbcTemplate jdbcTemplate;

    public SectorController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping
    public List<String> list() {
        return jdbcTemplate.queryForList("SELECT nombre_rubro FROM rubros_empleo WHERE activo = true ORDER BY nombre_rubro", String.class);
    }
}
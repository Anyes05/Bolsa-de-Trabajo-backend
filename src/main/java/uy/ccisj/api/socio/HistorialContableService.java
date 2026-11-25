package uy.ccisj.api.socio;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import jakarta.persistence.EntityManager;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class HistorialContableService {
    private final JdbcTemplate jdbc;
    private final EntityManager em;

    public HistorialContableService(JdbcTemplate jdbc, EntityManager em) {
        this.jdbc = jdbc;
        this.em = em;
    }

    public record Asiento(Long id, String entidad, Long entidadId, String tipo, BigDecimal monto,
                          OffsetDateTime registradoEn, String autor, String motivo, String datos) {}

    @Transactional(readOnly = true)
    public List<Asiento> listar(Long socioId) {
        if (em.find(Socio.class, socioId) == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "El socio no existe");
        }
        return jdbc.query("SELECT * FROM historial_contable WHERE socio_id = ? ORDER BY id", (rs, row) ->
                new Asiento(rs.getLong("id"), rs.getString("entidad"), rs.getLong("entidad_id"),
                        rs.getString("tipo"), rs.getBigDecimal("monto"),
                        rs.getObject("registrado_en", OffsetDateTime.class), rs.getString("autor"),
                        rs.getString("motivo"), rs.getString("datos")), socioId);
    }
}

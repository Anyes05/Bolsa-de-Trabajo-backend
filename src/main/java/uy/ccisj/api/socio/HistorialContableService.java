package uy.ccisj.api.socio;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import uy.ccisj.api.domain.EstadoCuota;

@Service
public class HistorialContableService {
    private final JdbcTemplate jdbc;
    private final EntityManager em;
    private final CajaService caja;

    public HistorialContableService(JdbcTemplate jdbc, EntityManager em, CajaService caja) {
        this.jdbc = jdbc;
        this.em = em;
        this.caja = caja;
    }

    public record Asiento(Long id, String entidad, Long entidadId, String tipo, BigDecimal monto,
                          OffsetDateTime registradoEn, String autor, String motivo, Long originalId, String datos) {}

    @Transactional(readOnly = true)
    public List<Asiento> listar(Long socioId) {
        if (em.find(Socio.class, socioId) == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "El socio no existe");
        }
        return jdbc.query("SELECT * FROM historial_contable WHERE socio_id = ? ORDER BY id", (rs, row) ->
                new Asiento(rs.getLong("id"), rs.getString("entidad"), rs.getLong("entidad_id"),
                        rs.getString("tipo"), rs.getBigDecimal("monto"),
                        rs.getObject("registrado_en", OffsetDateTime.class), rs.getString("autor"),
                        rs.getString("motivo"), rs.getObject("original_id", Long.class), rs.getString("datos")), socioId);
    }

    @Transactional
    public void anularCuota(Long socioId, Long cuotaId, String motivo) {
        Socio socio = em.find(Socio.class, socioId, LockModeType.PESSIMISTIC_WRITE);
        Cuota cuota = em.find(Cuota.class, cuotaId, LockModeType.PESSIMISTIC_WRITE);
        if (socio == null || cuota == null || !cuota.getSocio().getId().equals(socioId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "La cuota no existe");
        }
        if (cuota.getEstado() == EstadoCuota.ANULADA) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "La cuota ya está anulada");
        }
        if (jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM pagos WHERE socio_id = ?) "
                        + "OR EXISTS(SELECT 1 FROM reintegros WHERE cuota_id = ?)", Boolean.class, socioId, cuotaId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "La cuenta tiene pagos o reintegros vinculados; requiere conciliación antes de corregir");
        }
        ContabilidadContext.registrar(em, motivo.trim());
        cuota.setEstado(EstadoCuota.ANULADA);
        em.flush();
        caja.actualizarMorosidad(socio);
    }
}

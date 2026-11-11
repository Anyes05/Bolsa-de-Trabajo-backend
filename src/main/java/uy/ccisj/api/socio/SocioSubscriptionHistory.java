package uy.ccisj.api.socio;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class SocioSubscriptionHistory {
    private final JdbcTemplate jdbc;

    public SocioSubscriptionHistory(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public LocalDate today() {
        return LocalDate.now(ZoneId.of("America/Montevideo"));
    }

    public void lock(Long id) {
        if (jdbc.queryForList("SELECT id FROM socios WHERE id = ? FOR UPDATE", Long.class, id).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Socio no encontrado");
        }
    }

    public List<Entry> list(Long id) {
        return jdbc.query("""
                SELECT id, tipo, fecha, estado, registrado_at, origen
                FROM socio_historial_suscripcion WHERE socio_id = ?
                ORDER BY fecha DESC, id DESC
                """, (rs, row) -> new Entry(rs.getLong("id"), rs.getString("tipo"), rs.getObject("fecha", LocalDate.class),
                rs.getString("estado"), rs.getObject("registrado_at", OffsetDateTime.class), rs.getString("origen")), id);
    }

    public void record(Long id, String type, LocalDate date) {
        jdbc.update("INSERT INTO socio_historial_suscripcion (socio_id, tipo, fecha) VALUES (?, ?, ?)", id, type, date);
    }

    public void validateReentry(Long id, LocalDate date) {
        if (date == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La fecha de reingreso es obligatoria");
        LocalDate minimum = jdbc.queryForObject("""
                SELECT MAX(fecha) FROM socio_historial_suscripcion
                WHERE socio_id = ? AND tipo IN ('ALTA', 'BAJA', 'REINGRESO')
                """, LocalDate.class, id);
        if (minimum != null && date.isBefore(minimum)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La fecha de reingreso no puede ser anterior a la última alta o baja");
        }
        if (hasPending(id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ya existe un reingreso programado. Cancelalo antes de cambiar la fecha");
        }
    }

    public boolean hasPending(Long id) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS(SELECT 1 FROM socio_historial_suscripcion WHERE socio_id = ? AND estado = 'PENDIENTE')
                """, Boolean.class, id));
    }

    public void schedule(Long id, LocalDate date) {
        jdbc.update("""
                INSERT INTO socio_historial_suscripcion (socio_id, tipo, fecha, estado)
                VALUES (?, 'REINGRESO_PROGRAMADO', ?, 'PENDIENTE')
                """, id, date);
    }

    public void cancel(Long id) {
        if (jdbc.update("""
                UPDATE socio_historial_suscripcion SET estado = 'CANCELADO'
                WHERE socio_id = ? AND estado = 'PENDIENTE'
                """, id) == 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "El socio no tiene un reingreso pendiente");
        }
    }

    public List<Long> dueSocios() {
        return jdbc.queryForList("""
                SELECT socio_id FROM socio_historial_suscripcion WHERE estado = 'PENDIENTE' AND fecha <= ?
                ORDER BY fecha, id LIMIT 100
                """, Long.class, today());
    }

    public LocalDate applyDue(Long id) {
        return jdbc.query("""
                UPDATE socio_historial_suscripcion SET estado = 'APLICADO'
                WHERE socio_id = ? AND estado = 'PENDIENTE' AND fecha <= ? RETURNING fecha
                """, rs -> rs.next() ? rs.getObject(1, LocalDate.class) : null, id, today());
    }

    public record Entry(Long id, String tipo, LocalDate fecha, String estado, OffsetDateTime registradoAt, String origen) {}
}

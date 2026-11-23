package uy.ccisj.api.socio;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.sql.ResultSet;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.web.server.ResponseStatusException;

/**
 * Historial de suscripción con JdbcTemplate simulado: validaciones de reingreso,
 * cancelación y aplicación de reingresos programados.
 */
class SocioSubscriptionHistoryCoverageTest {
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final SocioSubscriptionHistory history = new SocioSubscriptionHistory(jdbc);
    private final LocalDate hoy = LocalDate.now(ZoneId.of("America/Montevideo"));

    @Test
    void todayUsaZonaDeMontevideo() {
        assertEquals(hoy, history.today());
    }

    @Test
    void lockFallaSiElSocioNoExiste() {
        when(jdbc.queryForList(anyString(), eq(Long.class), eq(1L))).thenReturn(List.of(1L));
        when(jdbc.queryForList(anyString(), eq(Long.class), eq(2L))).thenReturn(List.of());

        assertDoesNotThrow(() -> history.lock(1L));
        var error = assertThrows(ResponseStatusException.class, () -> history.lock(2L));
        assertEquals(404, error.getStatusCode().value());
    }

    @Test
    @SuppressWarnings("unchecked")
    void listMapeaLasEntradas() throws Exception {
        OffsetDateTime registrado = OffsetDateTime.parse("2026-01-01T10:00:00-03:00");
        ResultSet rs = mock(ResultSet.class);
        when(rs.getLong("id")).thenReturn(4L);
        when(rs.getString("tipo")).thenReturn("REINGRESO_PROGRAMADO");
        when(rs.getObject("fecha", LocalDate.class)).thenReturn(LocalDate.of(2026, 2, 1));
        when(rs.getString("estado")).thenReturn("PENDIENTE");
        when(rs.getObject("registrado_at", OffsetDateTime.class)).thenReturn(registrado);
        when(rs.getString("origen")).thenReturn("ADMIN");
        when(jdbc.query(anyString(), any(RowMapper.class), eq(9L)))
                .thenAnswer(invocation -> List.of(((RowMapper<Object>) invocation.getArgument(1)).mapRow(rs, 0)));

        assertEquals(List.of(new SocioSubscriptionHistory.Entry(4L, "REINGRESO_PROGRAMADO", LocalDate.of(2026, 2, 1),
                "PENDIENTE", registrado, "ADMIN")), history.list(9L));
    }

    @Test
    void recordYScheduleInsertanFilas() {
        LocalDate fecha = LocalDate.of(2026, 3, 1);
        history.record(9L, "BAJA", fecha);
        history.schedule(9L, fecha);

        verify(jdbc).update(contains("(socio_id, tipo, fecha) VALUES"), eq(9L), eq("BAJA"), eq(fecha));
        verify(jdbc).update(contains("'REINGRESO_PROGRAMADO'"), eq(9L), eq(fecha));
    }

    private void ultimaFecha(LocalDate minimo, Boolean pendiente) {
        when(jdbc.queryForObject(contains("MAX(fecha)"), eq(LocalDate.class), eq(9L))).thenReturn(minimo);
        when(jdbc.queryForObject(contains("EXISTS"), eq(Boolean.class), eq(9L))).thenReturn(pendiente);
    }

    @Test
    void validateReentryExigeFechaPosteriorYSinPendientes() {
        assertEquals(400, assertThrows(ResponseStatusException.class,
                () -> history.validateReentry(9L, null)).getStatusCode().value());

        ultimaFecha(LocalDate.of(2026, 5, 1), false);
        assertEquals(400, assertThrows(ResponseStatusException.class,
                () -> history.validateReentry(9L, LocalDate.of(2026, 4, 30))).getStatusCode().value());
        assertDoesNotThrow(() -> history.validateReentry(9L, LocalDate.of(2026, 5, 1)));

        ultimaFecha(null, true);
        assertEquals(409, assertThrows(ResponseStatusException.class,
                () -> history.validateReentry(9L, LocalDate.of(2000, 1, 1))).getStatusCode().value());
    }

    @Test
    void hasPendingTrataNuloComoFalso() {
        ultimaFecha(null, null);
        assertFalse(history.hasPending(9L));
        ultimaFecha(null, true);
        assertTrue(history.hasPending(9L));
        assertDoesNotThrow(() -> {
            ultimaFecha(null, false);
            history.validateReentry(9L, LocalDate.of(2000, 1, 1));
        });
    }

    @Test
    void cancelSinPendienteEsConflicto() {
        when(jdbc.update(contains("'CANCELADO'"), eq(1L))).thenReturn(1);
        when(jdbc.update(contains("'CANCELADO'"), eq(2L))).thenReturn(0);

        assertDoesNotThrow(() -> history.cancel(1L));
        assertEquals(409, assertThrows(ResponseStatusException.class, () -> history.cancel(2L)).getStatusCode().value());
    }

    @Test
    void dueSociosConsultaHastaHoy() {
        when(jdbc.queryForList(contains("estado = 'PENDIENTE' AND fecha <= ?"), eq(Long.class), eq(hoy)))
                .thenReturn(List.of(3L, 4L));

        assertEquals(List.of(3L, 4L), history.dueSocios());
    }

    @Test
    @SuppressWarnings("unchecked")
    void applyDueDevuelveLaFechaAplicadaONulo() throws Exception {
        ResultSet conFila = mock(ResultSet.class);
        when(conFila.next()).thenReturn(true);
        when(conFila.getObject(1, LocalDate.class)).thenReturn(LocalDate.of(2026, 1, 15));
        ResultSet sinFila = mock(ResultSet.class);
        when(jdbc.query(contains("RETURNING fecha"), any(ResultSetExtractor.class), eq(1L), eq(hoy)))
                .thenAnswer(invocation -> ((ResultSetExtractor<Object>) invocation.getArgument(1)).extractData(conFila));
        when(jdbc.query(contains("RETURNING fecha"), any(ResultSetExtractor.class), eq(2L), eq(hoy)))
                .thenAnswer(invocation -> ((ResultSetExtractor<Object>) invocation.getArgument(1)).extractData(sinFila));

        assertEquals(LocalDate.of(2026, 1, 15), history.applyDue(1L));
        assertNull(history.applyDue(2L));
    }
}

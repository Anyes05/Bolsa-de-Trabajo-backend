package uy.ccisj.api.socio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.time.OffsetDateTime;
import java.util.List;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

/** Listado del libro contable de un socio. */
class HistorialContableServiceCoverageTest {
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final EntityManager em = mock(EntityManager.class);
    private final HistorialContableService service = new HistorialContableService(jdbc, em);

    @Test
    @SuppressWarnings("unchecked")
    void listarMapeaCadaAsientoDelSocio() throws Exception {
        Socio socio = new Socio(null, "Empresa");
        ReflectionTestUtils.setField(socio, "id", 1L);
        when(em.find(Socio.class, 1L)).thenReturn(socio);
        OffsetDateTime registrado = OffsetDateTime.parse("2026-01-05T10:00:00-03:00");
        ResultSet rs = mock(ResultSet.class);
        when(rs.getLong("id")).thenReturn(100L);
        when(rs.getString("entidad")).thenReturn("CUOTA");
        when(rs.getLong("entidad_id")).thenReturn(10L);
        when(rs.getString("tipo")).thenReturn("EMISION");
        when(rs.getBigDecimal("monto")).thenReturn(new BigDecimal("10"));
        when(rs.getObject("registrado_en", OffsetDateTime.class)).thenReturn(registrado);
        when(rs.getString("autor")).thenReturn("JOB:FACTURACION");
        when(rs.getString("motivo")).thenReturn("Facturación del período 2026-01-01");
        when(rs.getString("datos")).thenReturn("{}");
        when(jdbc.query(anyString(), any(RowMapper.class), eq(1L))).thenAnswer(invocation -> {
            RowMapper<?> mapper = invocation.getArgument(1);
            return List.of(mapper.mapRow(rs, 0));
        });

        List<HistorialContableService.Asiento> asientos = service.listar(1L);

        assertEquals(List.of(new HistorialContableService.Asiento(100L, "CUOTA", 10L, "EMISION",
                new BigDecimal("10"), registrado, "JOB:FACTURACION", "Facturación del período 2026-01-01", "{}")),
                asientos);
    }

    @Test
    void listarDeSocioInexistenteDevuelve404() {
        var error = assertThrows(ResponseStatusException.class, () -> service.listar(2L));

        assertEquals(404, error.getStatusCode().value());
        verifyNoInteractions(jdbc);
    }
}

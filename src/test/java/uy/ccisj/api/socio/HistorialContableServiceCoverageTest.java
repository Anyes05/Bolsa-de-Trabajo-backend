package uy.ccisj.api.socio;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.Query;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;
import uy.ccisj.api.domain.EstadoCuota;

/** Listado del libro contable y anulación de cuotas con sus validaciones. */
class HistorialContableServiceCoverageTest {
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final EntityManager em = mock(EntityManager.class);
    private final CajaService caja = mock(CajaService.class);
    private final Query query = mock(Query.class);
    private final HistorialContableService service = new HistorialContableService(jdbc, em, caja);
    private final Socio socio = new Socio(null, "Empresa");
    private Cuota cuota;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(socio, "id", 1L);
        cuota = new Cuota(socio, LocalDate.of(2026, 1, 1), BigDecimal.TEN, LocalDate.of(2026, 2, 28));
        ReflectionTestUtils.setField(cuota, "id", 10L);
        when(em.createNativeQuery(anyString())).thenReturn(query);
        when(query.setParameter(anyString(), any())).thenReturn(query);
    }

    @Test
    void listarMapeaCadaAsientoDelSocio() throws Exception {
        when(em.find(Socio.class, 1L)).thenReturn(socio);
        OffsetDateTime registrado = OffsetDateTime.parse("2026-01-05T10:00:00-03:00");
        ResultSet rs = mock(ResultSet.class);
        when(rs.getLong("id")).thenReturn(100L);
        when(rs.getString("entidad")).thenReturn("CUOTA");
        when(rs.getLong("entidad_id")).thenReturn(10L);
        when(rs.getString("tipo")).thenReturn("ANULACION");
        when(rs.getBigDecimal("monto")).thenReturn(new BigDecimal("-10"));
        when(rs.getObject("registrado_en", OffsetDateTime.class)).thenReturn(registrado);
        when(rs.getString("autor")).thenReturn("ADMIN:a@example.test");
        when(rs.getString("motivo")).thenReturn("Error");
        when(rs.getObject("original_id", Long.class)).thenReturn(99L);
        when(rs.getString("datos")).thenReturn("{}");
        when(jdbc.query(anyString(), any(RowMapper.class), eq(1L))).thenAnswer(invocation -> {
            RowMapper<?> mapper = invocation.getArgument(1);
            return List.of(mapper.mapRow(rs, 0));
        });

        List<HistorialContableService.Asiento> asientos = service.listar(1L);

        assertEquals(List.of(new HistorialContableService.Asiento(100L, "CUOTA", 10L, "ANULACION",
                new BigDecimal("-10"), registrado, "ADMIN:a@example.test", "Error", 99L, "{}")), asientos);
    }

    @Test
    void listarDeSocioInexistenteDevuelve404() {
        var error = assertThrows(ResponseStatusException.class, () -> service.listar(2L));

        assertEquals(404, error.getStatusCode().value());
        verifyNoInteractions(jdbc);
    }

    @Test
    void anularCuotaInexistenteOAjenaDevuelve404() {
        Socio otro = new Socio(null, "Otra");
        ReflectionTestUtils.setField(otro, "id", 2L);
        when(em.find(Socio.class, 1L, LockModeType.PESSIMISTIC_WRITE)).thenReturn(socio);
        when(em.find(Socio.class, 2L, LockModeType.PESSIMISTIC_WRITE)).thenReturn(otro);
        when(em.find(Cuota.class, 10L, LockModeType.PESSIMISTIC_WRITE)).thenReturn(cuota);

        assertEquals(404, assertThrows(ResponseStatusException.class,
                () -> service.anularCuota(3L, 10L, "x")).getStatusCode().value(), "socio inexistente");
        assertEquals(404, assertThrows(ResponseStatusException.class,
                () -> service.anularCuota(1L, 11L, "x")).getStatusCode().value(), "cuota inexistente");
        assertEquals(404, assertThrows(ResponseStatusException.class,
                () -> service.anularCuota(2L, 10L, "x")).getStatusCode().value(), "cuota de otro socio");
        verifyNoInteractions(caja);
    }

    @Test
    void anularCuotaYaAnuladaOConPagosEsConflicto() {
        when(em.find(Socio.class, 1L, LockModeType.PESSIMISTIC_WRITE)).thenReturn(socio);
        when(em.find(Cuota.class, 10L, LockModeType.PESSIMISTIC_WRITE)).thenReturn(cuota);
        cuota.setEstado(EstadoCuota.ANULADA);
        assertEquals(409, assertThrows(ResponseStatusException.class,
                () -> service.anularCuota(1L, 10L, "x")).getStatusCode().value());

        cuota.setEstado(EstadoCuota.PENDIENTE);
        when(jdbc.queryForObject(anyString(), eq(Boolean.class), eq(1L), eq(10L))).thenReturn(true);
        var error = assertThrows(ResponseStatusException.class, () -> service.anularCuota(1L, 10L, "x"));

        assertEquals(409, error.getStatusCode().value());
        assertTrue(error.getReason().contains("conciliación"));
        assertEquals(EstadoCuota.PENDIENTE, cuota.getEstado());
        verifyNoInteractions(caja);
    }

    @Test
    void anularCuotaSinMovimientosLaMarcaAnuladaYRecalculaMorosidad() {
        when(em.find(Socio.class, 1L, LockModeType.PESSIMISTIC_WRITE)).thenReturn(socio);
        when(em.find(Cuota.class, 10L, LockModeType.PESSIMISTIC_WRITE)).thenReturn(cuota);
        when(jdbc.queryForObject(anyString(), eq(Boolean.class), eq(1L), eq(10L))).thenReturn(false);

        service.anularCuota(1L, 10L, "  Carga duplicada  ");

        assertEquals(EstadoCuota.ANULADA, cuota.getEstado());
        verify(query).setParameter("motivo", "Carga duplicada");
        verify(em).flush();
        verify(caja).actualizarMorosidad(socio);
    }
}

package uy.ccisj.api.socio;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.List;
import java.util.function.Supplier;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.SharedEntityManagerCreator;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import uy.ccisj.api.domain.EstadoMorosidad;
import uy.ccisj.api.catalogo.RubroEmpleo;
import uy.ccisj.api.catalogo.RubroEmpleoRepository;
import uy.ccisj.api.socio.dto.SocioUpdateDTO;
import uy.ccisj.api.user.UserRepository;

@EnabledIfSystemProperty(named = "testJdbcUrl", matches = ".+")
class SocioSubscriptionDatabaseTest {
    private static JdbcTemplate jdbc;
    private static LocalContainerEntityManagerFactoryBean factory;
    private SocioSubscriptionHistory history;
    private SocioService service;
    private TransactionTemplate tx;
    private CajaService caja;
    private Long socioId;
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 11);

    @BeforeAll
    static void migrate() {
        String schema = "subscriptions_" + java.util.UUID.randomUUID().toString().replace("-", "");
        var ds = uy.ccisj.api.support.DatabaseTestConfig.dataSource(schema);
        jdbc = new JdbcTemplate(ds);
        jdbc.execute("CREATE EXTENSION IF NOT EXISTS pgcrypto WITH SCHEMA public");
        Flyway.configure().dataSource(ds).schemas(schema).target("34").load().migrate();
        Long user = jdbc.queryForObject("""
                INSERT INTO usuarios (email, password_hash, rol, bps, activo, fecha_baja)
                VALUES ('legacy-history@example.test', 'test', 'SOCIO', 'legacy-history', FALSE, '2026-10-10T12:00:00Z') RETURNING id
                """, Long.class);
        jdbc.update("INSERT INTO socios (usuario_id, razon_social, fecha_alta, estado_morosidad) VALUES (?, 'Legacy', '2020-01-01', 'INACTIVO')", user);
        Flyway.configure().dataSource(ds).schemas(schema).load().migrate();
        assertEquals(2L, jdbc.queryForObject("SELECT count(*) FROM socio_historial_suscripcion WHERE origen = 'MIGRACION' AND socio_id = (SELECT id FROM socios WHERE razon_social = 'Legacy')", Long.class));
        factory = new LocalContainerEntityManagerFactoryBean();
        factory.setDataSource(ds);
        factory.setPackagesToScan("uy.ccisj.api");
        factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
        factory.afterPropertiesSet();
    }

    @BeforeEach
    void setUp() {
        var emf = factory.getObject();
        var repos = new JpaRepositoryFactory(SharedEntityManagerCreator.createSharedEntityManager(emf));
        history = spy(new SocioSubscriptionHistory(jdbc));
        doReturn(TODAY).when(history).today();
        caja = mock(CajaService.class);
        var rubros = mock(RubroEmpleoRepository.class);
        var rubro = mock(RubroEmpleo.class);
        when(rubro.isActivo()).thenReturn(true);
        when(rubros.findById(1L)).thenReturn(java.util.Optional.of(rubro));
        service = new SocioService(repos.getRepository(SocioRepository.class), repos.getRepository(UserRepository.class),
                null, rubros, null, caja, null, "http://example.test", history);
        tx = new TransactionTemplate(new JpaTransactionManager(emf));
        String key = java.util.UUID.randomUUID().toString().substring(0, 24);
        Long user = jdbc.queryForObject("INSERT INTO usuarios (email, password_hash, rol, bps) VALUES (?, 'test', 'SOCIO', ?) RETURNING id",
                Long.class, key + "@example.test", key);
        socioId = jdbc.queryForObject("INSERT INTO socios (usuario_id, razon_social, fecha_alta) VALUES (?, 'Test', '2020-01-01') RETURNING id", Long.class, user);
        history.record(socioId, "ALTA", LocalDate.of(2020, 1, 1));
    }

    @AfterAll
    static void close() {
        if (factory != null) factory.destroy();
    }

    @Test
    void retainsEveryCycleAndOriginalDateWithoutIssuingCharges() {
        inTx(() -> service.deactivateSocio(socioId));
        inTx(() -> service.deactivateSocio(socioId));
        assertEquals(1, history.list(socioId).stream().filter(e -> e.tipo().equals("BAJA")).count());
        inTx(() -> service.activateSocio(socioId, TODAY));
        assertTrue(inTx(() -> service.getSocioById(socioId)).activo());
        assertEquals(LocalDate.of(2020, 1, 1), inTx(() -> service.getSocioById(socioId)).fechaAlta());
        assertNull(inTx(() -> service.getSocioById(socioId)).fechaBaja());
        inTx(() -> service.deactivateSocio(socioId));
        inTx(() -> service.activateSocio(socioId, TODAY));
        assertEquals(5, new SocioSubscriptionHistory(jdbc).list(socioId).size());
        verify(caja, times(2)).actualizarMorosidad(any());
        verifyNoMoreInteractions(caja);
    }

    @Test
    void futureDateKeepsInactiveUntilDueAndRunsOnce() {
        inTx(() -> service.deactivateSocio(socioId));
        LocalDate future = TODAY.plusDays(3);
        inTx(() -> service.activateSocio(socioId, future));
        assertFalse(inTx(() -> service.getSocioById(socioId)).activo());
        assertTrue(history.hasPending(socioId));
        assertFalse(history.dueSocios().contains(socioId));
        tx.executeWithoutResult(s -> service.applyScheduledReentry(socioId));
        assertFalse(inTx(() -> service.getSocioById(socioId)).activo());
        doReturn(future).when(history).today();
        assertTrue(history.dueSocios().contains(socioId));
        tx.executeWithoutResult(s -> service.applyScheduledReentry(socioId));
        tx.executeWithoutResult(s -> service.applyScheduledReentry(socioId));
        assertTrue(inTx(() -> service.getSocioById(socioId)).activo());
        assertFalse(history.hasPending(socioId));
        assertEquals(1, history.list(socioId).stream().filter(e -> e.tipo().equals("REINGRESO")).count());
        assertTrue(history.list(socioId).stream().anyMatch(e -> e.tipo().equals("REINGRESO") && e.fecha().equals(future)));
        verify(caja, times(1)).actualizarMorosidad(any());
    }

    @Test
    void cancellationIsRetainedAndAllowsRescheduling() {
        inTx(() -> service.deactivateSocio(socioId));
        inTx(() -> service.activateSocio(socioId, TODAY.plusDays(1)));
        assertThrows(ResponseStatusException.class, () -> inTx(() -> service.activateSocio(socioId, TODAY.plusDays(2))));
        tx.executeWithoutResult(s -> service.cancelScheduledReentry(socioId));
        assertFalse(history.hasPending(socioId));
        assertTrue(history.list(socioId).stream().anyMatch(e -> e.estado().equals("CANCELADO")));
        inTx(() -> service.activateSocio(socioId, TODAY.plusDays(2)));
        assertTrue(history.hasPending(socioId));
        assertFalse(inTx(() -> service.getSocioById(socioId)).activo());
    }

    @Test
    void rejectsMissingEarlierAndRepeatedReentryAndRollsBackOnFailure() throws Exception {
        var mvc = MockMvcBuilders.standaloneSetup(new SocioController(service, history)).build();
        for (String body : List.of("{}", "{\"fecha\":null}", "{\"fecha\":\"no-es-fecha\"}")) {
            mvc.perform(post("/admin/socios/" + socioId + "/activar").contentType("application/json").content(body))
                    .andExpect(status().isBadRequest());
        }
        assertThrows(ResponseStatusException.class, () -> inTx(() -> service.activateSocio(socioId, TODAY)));
        inTx(() -> service.deactivateSocio(socioId));
        assertThrows(ResponseStatusException.class, () -> inTx(() -> service.activateSocio(socioId, TODAY.minusDays(1))));
        doThrow(new IllegalStateException("Simulated accounting error")).when(caja).actualizarMorosidad(any());
        assertThrows(IllegalStateException.class, () -> inTx(() -> service.activateSocio(socioId, TODAY)));
        assertFalse(inTx(() -> service.getSocioById(socioId)).activo());
        assertEquals(2, history.list(socioId).size(), "El reingreso y el estado deben revertirse juntos");
        assertEquals(EstadoMorosidad.INACTIVO, inTx(() -> service.getSocioById(socioId)).estadoMorosidad());
    }

    private <T> T inTx(Supplier<T> operation) {
        return tx.execute(status -> operation.get());
    }

    @Test
    void editingStatusCannotBypassDatedReentry() {
        inTx(() -> service.deactivateSocio(socioId));
        var dto = new SocioUpdateDTO(null, null, "Test", socioId.toString(), "123", null, "Calle", "1",
                "San José de Mayo", 1L, false, null, TODAY, EstadoMorosidad.AL_DIA);
        assertThrows(ResponseStatusException.class, () -> inTx(() -> service.updateSocio(socioId, dto)));
        assertFalse(inTx(() -> service.getSocioById(socioId)).activo());
        assertEquals(2, history.list(socioId).size());
    }

    @Test
    void simultaneousSchedulingCreatesOnlyOnePendingEntry() throws Exception {
        inTx(() -> service.deactivateSocio(socioId));
        var start = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.Callable<Integer> operation = () -> {
            start.await();
            try {
                inTx(() -> service.activateSocio(socioId, TODAY.plusDays(2)));
                return 200;
            } catch (ResponseStatusException exception) {
                return exception.getStatusCode().value();
            }
        };
        try (var pool = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var first = pool.submit(operation);
            var second = pool.submit(operation);
            start.countDown();
            var results = List.of(first.get(10, java.util.concurrent.TimeUnit.SECONDS),
                    second.get(10, java.util.concurrent.TimeUnit.SECONDS));
            assertTrue(results.containsAll(List.of(200, 409)));
        }
        assertEquals(1, history.list(socioId).stream().filter(e -> e.estado().equals("PENDIENTE")).count());
    }
}

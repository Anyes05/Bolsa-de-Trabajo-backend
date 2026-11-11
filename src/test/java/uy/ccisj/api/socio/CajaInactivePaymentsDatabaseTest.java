package uy.ccisj.api.socio;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.SharedEntityManagerCreator;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import uy.ccisj.api.domain.EstadoMorosidad;
import uy.ccisj.api.domain.MetodoPago;
import uy.ccisj.api.socio.dto.CuentaCajaDTO;
import uy.ccisj.api.socio.dto.RegistrarCobroDTO;
import uy.ccisj.api.socio.dto.RegistrarReintegroDTO;

@EnabledIfSystemProperty(named = "cajaTestJdbcUrl", matches = ".+")
class CajaInactivePaymentsDatabaseTest {
    private static JdbcTemplate jdbc;
    private static LocalContainerEntityManagerFactoryBean factory;
    private CajaService caja;
    private TransactionTemplate tx;
    private Long socioId;
    private Long userId;

    @BeforeAll
    static void migrate() {
        String schema = "cash_" + java.util.UUID.randomUUID().toString().replace("-", "");
        var ds = new DriverManagerDataSource(System.getProperty("cajaTestJdbcUrl") + "?currentSchema=" + schema + ",public",
                "cash_test", "cash_test");
        jdbc = new JdbcTemplate(ds);
        jdbc.execute("CREATE EXTENSION IF NOT EXISTS pgcrypto WITH SCHEMA public");
        Flyway.configure().dataSource(ds).schemas(schema).load().migrate();
        factory = new LocalContainerEntityManagerFactoryBean();
        factory.setDataSource(ds);
        factory.setPackagesToScan("uy.ccisj.api");
        factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
        factory.afterPropertiesSet();
    }

    @BeforeEach
    void setUp() {
        var emf = factory.getObject();
        var em = SharedEntityManagerCreator.createSharedEntityManager(emf);
        var repos = new JpaRepositoryFactory(em);
        caja = new CajaService(repos.getRepository(SocioRepository.class), repos.getRepository(CuotaRepository.class),
                repos.getRepository(PagoRepository.class), repos.getRepository(ReintegroRepository.class),
                repos.getRepository(ReintegroProgramadoRepository.class), em);
        tx = new TransactionTemplate(new JpaTransactionManager(emf));
        String key = java.util.UUID.randomUUID().toString().substring(0, 24);
        userId = jdbc.queryForObject("""
                INSERT INTO usuarios (email, password_hash, rol, bps, activo, fecha_baja)
                VALUES (?, 'test', 'SOCIO', ?, FALSE, '2026-10-10T12:00:00Z') RETURNING id
                """, Long.class, key + "@example.test", key);
        socioId = jdbc.queryForObject("""
                INSERT INTO socios (usuario_id, razon_social, fecha_alta, estado_morosidad)
                VALUES (?, 'Cobro inactivo', '2020-01-01', 'INACTIVO') RETURNING id
                """, Long.class, userId);
        jdbc.update("INSERT INTO socio_historial_suscripcion (socio_id, tipo, fecha) VALUES (?, 'ALTA', '2020-01-01'), (?, 'BAJA', '2026-10-10')",
                socioId, socioId);
    }

    @AfterAll
    static void close() {
        if (factory != null) factory.destroy();
    }

    @Test
    void partialAndFullPaymentsPersistWithoutReactivatingOrChangingSubscriptionHistory() {
        addCuota();
        var historyBefore = new SocioSubscriptionHistory(jdbc).list(socioId);
        var first = pay("40");
        assertEquals(EstadoMorosidad.INACTIVO, first.estadoMorosidad());
        assertEquals(0, new BigDecimal("60").compareTo(first.saldo()));
        var last = pay("60");
        assertEquals(EstadoMorosidad.INACTIVO, last.estadoMorosidad());
        assertEquals(0, last.saldo().signum());
        assertFalse(jdbc.queryForObject("SELECT activo FROM usuarios WHERE id = ?", Boolean.class, userId));
        assertEquals("2026-10-10", jdbc.queryForObject("SELECT fecha_baja::date::text FROM usuarios WHERE id = ?", String.class, userId));
        assertEquals("2020-01-01", jdbc.queryForObject("SELECT fecha_alta::text FROM socios WHERE id = ?", String.class, socioId));
        assertEquals(historyBefore, new SocioSubscriptionHistory(jdbc).list(socioId));
        assertEquals(2L, jdbc.queryForObject("SELECT count(*) FROM pagos WHERE socio_id = ?", Long.class, socioId));
        assertEquals("PAGADO", jdbc.queryForObject("SELECT estado FROM cuotas WHERE socio_id = ?", String.class, socioId));
        assertEquals(0, inTx(() -> caja.getHistorial(socioId)).saldo().signum());
        assertEquals(409, assertThrows(ResponseStatusException.class, () -> pay("1")).getStatusCode().value());
    }

    @Test
    void rejectsInactiveWithNoDebtOrCreditAndDoesNotSaveAPayment() {
        assertEquals(409, assertThrows(ResponseStatusException.class, () -> pay("10")).getStatusCode().value());
        jdbc.update("INSERT INTO pagos (socio_id, monto_cobrado, fecha_emision) VALUES (?, 10, CURRENT_DATE)", socioId);
        assertEquals(409, assertThrows(ResponseStatusException.class, () -> pay("10")).getStatusCode().value());
        assertEquals(1L, jdbc.queryForObject("SELECT count(*) FROM pagos WHERE socio_id = ?", Long.class, socioId));
        assertEquals(0, new BigDecimal("-10").compareTo(inTx(() -> caja.getHistorial(socioId)).saldo()));
    }

    @Test
    void includesOutstandingNonOverdueDebtInSummaryEvenWhenInactive() {
        addCuota();
        var summary = inTx(() -> caja.getResumen());
        var account = summary.cuentas().stream().filter(c -> c.socioId().equals(socioId)).findFirst().orElseThrow();
        assertEquals(EstadoMorosidad.INACTIVO, account.estadoMorosidad());
        assertEquals(0, account.deudaVencida().signum());
        assertEquals(summary.cuentas().stream().filter(c -> c.saldo().signum() > 0).count(), summary.pendiente());
        assertTrue(summary.pendiente() > 0);
        assertEquals(0, pay("100").saldo().signum());
    }

    @Test
    void disabledUserIsInactiveEvenIfStoredMorosityIsNotInactive() {
        jdbc.update("UPDATE socios SET estado_morosidad = 'MOROSO' WHERE id = ?", socioId);
        assertEquals(409, assertThrows(ResponseStatusException.class, () -> pay("10")).getStatusCode().value());
        addCuota();
        assertEquals(EstadoMorosidad.INACTIVO, pay("100").estadoMorosidad());
        assertFalse(jdbc.queryForObject("SELECT activo FROM usuarios WHERE id = ?", Boolean.class, userId));
    }

    @Test
    void activeAccountsStillAcceptAdvancePayments() {
        jdbc.update("UPDATE socios SET estado_morosidad = 'AL_DIA' WHERE id = ?", socioId);
        jdbc.update("UPDATE usuarios SET activo = TRUE, fecha_baja = NULL WHERE id = ?", userId);
        var account = pay("25");
        assertEquals(EstadoMorosidad.AL_DIA, account.estadoMorosidad());
        assertEquals(0, new BigDecimal("-25").compareTo(account.saldo()));
    }

    @Test
    void reintegroOnlyDebtCanBePaidButNewChargesRemainBlocked() {
        inTx(() -> {
            var em = SharedEntityManagerCreator.createSharedEntityManager(factory.getObject());
            em.persist(new Reintegro(em.find(Socio.class, socioId), "Gasto previo a la baja",
                    new BigDecimal("35"), LocalDate.now().minusMonths(2),
                    LocalDate.now().minusDays(1), null, "PUNTUAL"));
            return null;
        });
        assertEquals(409, assertThrows(ResponseStatusException.class, () -> inTx(() -> caja.registrarReintegro(
                socioId, new RegistrarReintegroDTO("Nuevo gasto", BigDecimal.TEN, false)))).getStatusCode().value());
        var account = pay("35");
        assertNull(account.cuotaId());
        assertEquals(0, account.saldo().signum());
        assertEquals(EstadoMorosidad.INACTIVO, account.estadoMorosidad());
    }

    @Test
    void simultaneousFullPaymentsRecheckDebtUnderLock() throws Exception {
        addCuota();
        var start = new CountDownLatch(1);
        Callable<Integer> operation = () -> {
            start.await();
            try {
                pay("100");
                return 200;
            } catch (ResponseStatusException exception) {
                return exception.getStatusCode().value();
            }
        };
        try (var pool = Executors.newFixedThreadPool(2)) {
            var first = pool.submit(operation);
            var second = pool.submit(operation);
            start.countDown();
            assertTrue(List.of(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS))
                    .containsAll(List.of(200, 409)));
        }
        assertEquals(1L, jdbc.queryForObject("SELECT count(*) FROM pagos WHERE socio_id = ?", Long.class, socioId));
        assertEquals(0, inTx(() -> caja.getHistorial(socioId)).saldo().signum());
    }

    private void addCuota() {
        jdbc.update("""
                INSERT INTO cuotas (socio_id, periodo, monto, fecha_vencimiento)
                VALUES (?, '2026-01-01', 100, ?)
                """, socioId, LocalDate.now().plusDays(30));
    }

    private CuentaCajaDTO pay(String amount) {
        return inTx(() -> caja.registrarCobro(new RegistrarCobroDTO(socioId, MetodoPago.EFECTIVO,
                new BigDecimal(amount), null, "Cobro de deuda anterior a la baja")));
    }

    private <T> T inTx(Supplier<T> operation) {
        return tx.execute(status -> operation.get());
    }
}

package uy.ccisj.api.socio;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.dao.DataAccessException;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.jpa.*;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.support.TransactionTemplate;
import uy.ccisj.api.domain.MetodoPago;
import uy.ccisj.api.socio.dto.GenerarFacturacionDTO;
import uy.ccisj.api.socio.dto.RegistrarCobroDTO;
import uy.ccisj.api.socio.dto.RegistrarReintegroDTO;

@EnabledIfSystemProperty(named = "testJdbcUrl", matches = ".+")
class HistorialContableDatabaseTest {
    private static JdbcTemplate jdbc;
    private static LocalContainerEntityManagerFactoryBean factory;
    private static Long backfillSocio;
    private static String runtimeRole;
    private TransactionTemplate tx;
    private CajaService caja;
    private TarifaService tarifas;
    private HistorialContableService historial;
    private Long socioId;

    @BeforeAll
    static void migrate() {
        String schema = "ledger_" + java.util.UUID.randomUUID().toString().replace("-", "");
        var ds = uy.ccisj.api.support.DatabaseTestConfig.dataSource(schema);
        jdbc = new JdbcTemplate(ds);
        jdbc.execute("CREATE EXTENSION IF NOT EXISTS pgcrypto WITH SCHEMA public");
        Flyway.configure().dataSource(ds).schemas(schema).target("37").load().migrate();
        backfillSocio = crearSocio();
        Long cuota = jdbc.queryForObject("""
                INSERT INTO cuotas(socio_id, periodo, monto, fecha_vencimiento, estado)
                VALUES (?, '2024-01-01', 100, '2024-01-31', 'ANULADA') RETURNING id
                """, Long.class, backfillSocio);
        jdbc.update("""
                INSERT INTO pagos(socio_id, cuota_id, monto_cobrado, fecha_emision, metodo_pago)
                VALUES (?, ?, 50, '2024-01-05', 'EFECTIVO')
                """, backfillSocio, cuota);
        jdbc.update("""
                INSERT INTO reintegros(socio_id, cuota_id, concepto, monto, fecha, fecha_vencimiento)
                VALUES (?, ?, 'Anterior', 20, '2024-01-01', '2024-01-31')
                """, backfillSocio, cuota);
        Flyway.configure().dataSource(ds).schemas(schema).load().migrate();
        runtimeRole = "ledger_runtime_" + java.util.UUID.randomUUID().toString().replace("-", "");
        jdbc.execute("CREATE ROLE " + runtimeRole);
        jdbc.execute("GRANT USAGE ON SCHEMA " + schema + " TO " + runtimeRole);
        jdbc.execute("GRANT SELECT, INSERT, UPDATE, DELETE ON cuotas, pagos, reintegros TO " + runtimeRole);
        jdbc.execute("GRANT SELECT ON historial_contable TO " + runtimeRole);
        jdbc.execute("GRANT USAGE ON ALL SEQUENCES IN SCHEMA " + schema + " TO " + runtimeRole);
        factory = new LocalContainerEntityManagerFactoryBean();
        factory.setDataSource(ds);
        factory.setPackagesToScan("uy.ccisj.api");
        factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
        factory.afterPropertiesSet();
    }

    private static Long crearSocio() {
        String key = java.util.UUID.randomUUID().toString().substring(0, 24);
        Long usuario = jdbc.queryForObject("""
                INSERT INTO usuarios(email, password_hash, rol, bps)
                VALUES (?, 'test', 'SOCIO', ?) RETURNING id
                """, Long.class, key + "@example.test", key);
        return jdbc.queryForObject("""
                INSERT INTO socios(usuario_id, razon_social, fecha_alta, estado_morosidad)
                VALUES (?, 'Contabilidad test', '2020-01-01', 'AL_DIA') RETURNING id
                """, Long.class, usuario);
    }

    @BeforeEach
    void setUp() {
        var emf = factory.getObject();
        var em = SharedEntityManagerCreator.createSharedEntityManager(emf);
        var repos = new JpaRepositoryFactory(em);
        caja = new CajaService(repos.getRepository(SocioRepository.class), repos.getRepository(CuotaRepository.class),
                repos.getRepository(PagoRepository.class), repos.getRepository(ReintegroRepository.class),
                repos.getRepository(ReintegroProgramadoRepository.class), em);
        tarifas = new TarifaService(repos.getRepository(TarifaRepository.class), repos.getRepository(CuotaRepository.class),
                repos.getRepository(SocioRepository.class), caja);
        historial = new HistorialContableService(jdbc, em, caja);
        tx = new TransactionTemplate(new JpaTransactionManager(emf));
        socioId = crearSocio();
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @AfterAll
    static void close() {
        if (factory != null) factory.destroy();
        if (runtimeRole != null) {
            jdbc.execute("DROP OWNED BY " + runtimeRole);
            jdbc.execute("DROP ROLE " + runtimeRole);
        }
    }

    private Long cuota() {
        return jdbc.queryForObject("""
                INSERT INTO cuotas(socio_id, periodo, monto, fecha_vencimiento)
                VALUES (?, '2026-01-01', 100, '2026-01-31') RETURNING id
                """, Long.class, socioId);
    }

    private long count() {
        return jdbc.queryForObject("SELECT count(*) FROM historial_contable WHERE socio_id = ?", Long.class, socioId);
    }

    @Test
    void backfillPreservesCancelledOriginalsAndTheirLinkedPayments() {
        var entries = tx.execute(s -> historial.listar(backfillSocio));
        assertEquals(6, entries.size());
        assertEquals(0, entries.stream().map(HistorialContableService.Asiento::monto)
                .reduce(BigDecimal.ZERO, BigDecimal::add).signum());
        assertTrue(entries.stream().allMatch(e -> e.autor().equals("MIGRACION")));
        assertEquals(3, entries.stream().filter(e -> e.originalId() != null).count());
    }

    @Test
    void directSqlCannotRewriteDeleteOrTruncateHistoryOrIssuedAmounts() {
        Long cuota = cuota();
        assertThrows(DataAccessException.class, () -> jdbc.update("UPDATE historial_contable SET monto=0 WHERE socio_id=?", socioId));
        assertThrows(DataAccessException.class, () -> jdbc.update("DELETE FROM historial_contable WHERE socio_id=?", socioId));
        assertThrows(DataAccessException.class, () -> jdbc.execute("TRUNCATE historial_contable"));
        assertThrows(DataAccessException.class, () -> jdbc.update("UPDATE cuotas SET monto=1 WHERE id=?", cuota));
        assertThrows(DataAccessException.class, () -> jdbc.update("DELETE FROM cuotas WHERE id=?", cuota));
        assertThrows(DataAccessException.class, () -> jdbc.execute("TRUNCATE cuotas CASCADE"));
        assertEquals(1, count());
    }

    @Test
    void restrictedRuntimeRoleCanEmitThroughTriggerButCannotInsertJournalOrDisableGuards() {
        tx.executeWithoutResult(s -> {
            jdbc.execute("SET LOCAL ROLE " + runtimeRole);
            cuota();
        });
        assertEquals(1, count());
        assertThrows(DataAccessException.class, () -> tx.executeWithoutResult(s -> {
            jdbc.execute("SET LOCAL ROLE " + runtimeRole);
            jdbc.update("INSERT INTO historial_contable(socio_id, entidad, entidad_id, tipo, monto, autor, motivo, datos) "
                    + "VALUES (?, 'PAGO', 999, 'EMISION', -100, 'falso', 'falso', '{}')", socioId);
        }));
        assertThrows(DataAccessException.class, () -> tx.executeWithoutResult(s -> {
            jdbc.execute("SET LOCAL ROLE " + runtimeRole);
            jdbc.execute("ALTER TABLE cuotas DISABLE TRIGGER cuotas_historial");
        }));
        assertThrows(DataAccessException.class, () -> tx.executeWithoutResult(s -> {
            jdbc.execute("SET LOCAL ROLE " + runtimeRole);
            jdbc.execute("CREATE TEMP TABLE fake_cuotas (LIKE cuotas)");
            jdbc.execute("CREATE TRIGGER fake_journal BEFORE INSERT ON fake_cuotas "
                    + "FOR EACH ROW EXECUTE FUNCTION registrar_historial_contable()");
        }));
        assertEquals(1, count());
    }

    @Test
    void cancellingThenRollingBackRestoresBothDebtAndJournal() {
        Long cuota = cuota();
        assertThrows(IllegalStateException.class, () -> tx.executeWithoutResult(s -> {
            historial.anularCuota(socioId, cuota, "Se revierte la transacción");
            throw new IllegalStateException("rollback");
        }));
        assertEquals(1, count());
        assertEquals(new BigDecimal("100.00"), tx.execute(s -> caja.getHistorial(socioId)).saldo());
    }

    @Test
    void cancellationAppendsReverseWithAdminReasonAndKeepsOriginalAndBalance() {
        Long cuota = cuota();
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "admin@example.test", null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
        tx.executeWithoutResult(s -> historial.anularCuota(socioId, cuota, "Emisión duplicada"));
        var entries = tx.execute(s -> historial.listar(socioId));
        assertEquals(2, entries.size());
        assertEquals(entries.get(0).id(), entries.get(1).originalId());
        assertEquals("ADMIN:admin@example.test", entries.get(1).autor());
        assertEquals("Emisión duplicada", entries.get(1).motivo());
        assertEquals(new BigDecimal("-100.00"), entries.get(1).monto());
        assertEquals(0, tx.execute(s -> caja.getHistorial(socioId)).saldo().signum());
        assertThrows(RuntimeException.class, () -> tx.executeWithoutResult(s -> historial.anularCuota(socioId, cuota, "Otra")));
        assertThrows(DataAccessException.class, () -> jdbc.update("UPDATE cuotas SET estado='PENDIENTE' WHERE id=?", cuota));
        assertEquals(2, count());
    }

    @Test
    void paymentAllocationAndRecurringMetadataStayMutableWithoutDuplicateEvents() {
        Long cuota = cuota();
        tx.executeWithoutResult(s -> caja.registrarReintegro(socioId,
                new RegistrarReintegroDTO("Timbre", new BigDecimal("20"), true)));
        tx.executeWithoutResult(s -> caja.registrarCobro(
                new RegistrarCobroDTO(socioId, MetodoPago.EFECTIVO, new BigDecimal("120"), null, null)));
        assertEquals(3, count());
        assertEquals("PAGADO", jdbc.queryForObject("SELECT estado FROM cuotas WHERE id=?", String.class, cuota));
        assertEquals(0, tx.execute(s -> caja.getHistorial(socioId)).saldo().signum());
        assertThrows(RuntimeException.class, () -> tx.executeWithoutResult(s -> historial.anularCuota(socioId, cuota, "No permitido")));
        assertThrows(DataAccessException.class, () -> jdbc.update("UPDATE pagos SET monto_cobrado=1 WHERE socio_id=?", socioId));
        assertThrows(DataAccessException.class, () -> jdbc.update("DELETE FROM reintegros WHERE socio_id=?", socioId));
        Long programado = jdbc.queryForObject("SELECT id FROM reintegros_programados WHERE socio_id=?", Long.class, socioId);
        tx.executeWithoutResult(s -> caja.eliminarProgramado(socioId, programado));
        assertEquals(3, count());
    }

    @Test
    void rollbackDoesNotLeaveHistoryAndContextDoesNotLeakAcrossTransactions() {
        assertThrows(IllegalStateException.class, () -> tx.executeWithoutResult(s -> {
            caja.registrarCobro(new RegistrarCobroDTO(socioId, MetodoPago.EFECTIVO, BigDecimal.TEN, null, null));
            throw new IllegalStateException("rollback");
        }));
        assertEquals(0, count());
        cuota();
        assertEquals("SISTEMA:SQL", jdbc.queryForObject(
                "SELECT autor FROM historial_contable WHERE socio_id=?", String.class, socioId));
    }

    @Test
    void billingJobIsIdentifiedAndRetryDoesNotDuplicateJournalEntries() {
        jdbc.update("INSERT INTO tarifas(anio, monto_base, dia_vencimiento) VALUES (2098, 100, 10)");
        var dto = new GenerarFacturacionDTO(LocalDate.of(2098, 1, 1));
        tx.executeWithoutResult(s -> tarifas.generarFacturacion(dto));
        long first = count();
        tx.executeWithoutResult(s -> tarifas.generarFacturacion(dto));
        assertEquals(first, count());
        assertEquals(1, first);
        assertEquals("JOB:FACTURACION", jdbc.queryForObject(
                "SELECT autor FROM historial_contable WHERE socio_id=?", String.class, socioId));
    }
}

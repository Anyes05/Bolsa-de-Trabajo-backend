package uy.ccisj.api.user;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.List;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import uy.ccisj.api.support.DatabaseTestConfig;

@EnabledIfSystemProperty(named = "resetDisposableDatabase", matches = "true")
class OperationalResetDatabaseTest {
    @Test
    void migrateResetAndRestartWithoutRecreatingMembers() throws Exception {
        assertEquals("jdbc:postgresql://127.0.0.1:55439/reset_verification",
                System.getProperty("testJdbcUrl"), "Reset test requires the dedicated local disposable database");
        var ds = DatabaseTestConfig.dataSource("public");
        var jdbc = new JdbcTemplate(ds);
        assertEquals("reset_verification", jdbc.queryForObject("SELECT current_database()", String.class));
        assertEquals(0, jdbc.queryForObject(
                "SELECT count(*) FROM pg_tables WHERE schemaname = 'public'", Integer.class),
                "Reset test requires a fresh empty database");
        jdbc.execute("CREATE EXTENSION IF NOT EXISTS pgcrypto");
        var flyway = Flyway.configure().dataSource(ds).load();
        flyway.migrate();
        var encoder = new BCryptPasswordEncoder();
        var bootstrap = new BootstrapUsers(jdbc, encoder, "admin@example.test", "OnlySyntheticTest-123");
        bootstrap.run();
        jdbc.update("UPDATE usuarios SET foto_perfil = decode('010203','hex'), foto_mime = 'image/png' WHERE rol='ADMIN'");
        var admins = jdbc.queryForList("SELECT to_jsonb(u)::text AS data FROM usuarios u WHERE rol='ADMIN' ORDER BY id");
        var rubros = jdbc.queryForList("SELECT * FROM rubros_empleo ORDER BY id");
        var history = jdbc.queryForList("SELECT * FROM flyway_schema_history ORDER BY installed_rank");
        Long user = jdbc.queryForObject("""
            INSERT INTO usuarios(email,password_hash,rol,bps) VALUES ('member@example.test','synthetic','SOCIO','123456')
            RETURNING id
            """, Long.class);
        Long socio = jdbc.queryForObject("""
            INSERT INTO socios(usuario_id,razon_social,fecha_alta,estado_morosidad)
            VALUES (?,'Synthetic',CURRENT_DATE,'AL_DIA') RETURNING id
            """, Long.class, user);
        jdbc.update("""
            INSERT INTO cuotas(socio_id,periodo,monto,fecha_vencimiento,estado)
            VALUES (?,'2026-01-01',100,'2026-01-31','PENDIENTE')
            """, socio);
        jdbc.update("INSERT INTO cv_archivos_pendientes_borrado(ruta) VALUES ('postulantes/42/cvs/test.pdf')");
        assertFalse(jdbc.queryForList("SELECT * FROM historial_contable").isEmpty());
        int sociosBefore = jdbc.queryForObject("SELECT count(*) FROM socios", Integer.class);
        runReset(true, 3, "not_the_target");
        jdbc.execute("CREATE TABLE unexpected_reset_table(id bigint)");
        runReset(true, 3);
        jdbc.execute("DROP TABLE unexpected_reset_table");
        jdbc.execute("ALTER TABLE cuotas DISABLE TRIGGER cuotas_historial");
        runReset(true, 3);
        jdbc.execute("ALTER TABLE cuotas ENABLE TRIGGER cuotas_historial");
        jdbc.update("INSERT INTO cv_archivos_pendientes_borrado(ruta) VALUES ('admin/protected.png')");
        runReset(true, 3);
        jdbc.update("DELETE FROM cv_archivos_pendientes_borrado WHERE ruta='admin/protected.png'");
        assertEquals(sociosBefore, jdbc.queryForObject("SELECT count(*) FROM socios", Integer.class));
        runReset(false, 0);
        assertEquals(sociosBefore, jdbc.queryForObject("SELECT count(*) FROM socios", Integer.class));
        runReset(true, 0);
        flyway.validate();
        flyway.migrate();
        bootstrap.run();
        assertEquals(admins, jdbc.queryForList("SELECT to_jsonb(u)::text AS data FROM usuarios u ORDER BY id"));
        assertEquals(rubros, jdbc.queryForList("SELECT * FROM rubros_empleo ORDER BY id"));
        assertEquals(history, jdbc.queryForList("SELECT * FROM flyway_schema_history ORDER BY installed_rank"));
        List<String> operational = jdbc.queryForList("""
            SELECT tablename FROM pg_tables WHERE schemaname='public'
            AND tablename NOT IN ('usuarios','rubros_empleo','flyway_schema_history')
            """, String.class);
        for (String table : operational) {
            assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM " + table, Integer.class), table);
        }
        for (String table : List.of("historial_contable", "cuotas", "pagos", "reintegros")) {
            assertThrows(org.springframework.dao.DataAccessException.class, () -> jdbc.execute("TRUNCATE " + table + " CASCADE"));
        }
        assertEquals(0, jdbc.queryForObject("""
            SELECT count(*) FROM pg_trigger WHERE NOT tgisinternal AND tgenabled <> 'O'
            """, Integer.class));
        assertThrows(org.springframework.dao.DataAccessException.class,
                () -> jdbc.execute("UPDATE historial_contable SET motivo='Synthetic forbidden update'"));
    }

    private void runReset(boolean apply, int exit) throws Exception {
        runReset(apply, exit, "reset_verification");
    }

    private void runReset(boolean apply, int exit, String expectedDatabase) throws Exception {
        var command = new ProcessBuilder("psql", "-X", "-qAt", "-h", "127.0.0.1", "-p", "55439",
                "-U", "ccisj_test", "-d", "reset_verification", "-v", "database=" + expectedDatabase,
                "-v", "apply=" + apply, "-f", Path.of("ops", "reset", "reset.sql").toString());
        command.environment().put("PGPASSWORD", "ccisj_test");
        var process = command.redirectErrorStream(true).start();
        var output = new String(process.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        assertEquals(exit, process.waitFor(), output);
        if (exit == 0) {
            assertTrue(output.contains("postulantes/42/cvs/test.pdf"), "Exact CV key manifest");
        }
    }
}

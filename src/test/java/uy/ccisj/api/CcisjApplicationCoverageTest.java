package uy.ccisj.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Base64;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.web.SecurityFilterChain;
import uy.ccisj.api.storage.S3StorageService;
import uy.ccisj.api.support.DatabaseTestConfig;

/**
 * Arranca la aplicacion completa por su main contra un schema aislado del Postgres de prueba,
 * verifica que la cadena de seguridad real responde y luego cierra el contexto.
 */
@EnabledIfSystemProperty(named = "testJdbcUrl", matches = ".+")
class CcisjApplicationCoverageTest {
    private static volatile ConfigurableApplicationContext contexto;
    private String schema;

    /** Fuente extra registrada por argumento para poder cerrar el contexto que crea main. */
    public static class CapturaDeContexto implements ApplicationContextAware {
        @Override
        public void setApplicationContext(ApplicationContext applicationContext) {
            contexto = (ConfigurableApplicationContext) applicationContext;
        }
    }

    @AfterEach
    void cerrar() {
        if (contexto != null) {
            contexto.close();
            contexto = null;
        }
        if (schema != null) {
            new JdbcTemplate(DatabaseTestConfig.dataSource("public")).execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
        }
    }

    @Test
    void mainLevantaLaApiConSeguridadYMigraciones() throws Exception {
        schema = "cov_auth_infra_app_" + UUID.randomUUID().toString().replace("-", "");
        new JdbcTemplate(DatabaseTestConfig.dataSource("public"))
                .execute("CREATE EXTENSION IF NOT EXISTS pgcrypto WITH SCHEMA public");
        String url = System.getProperty("testJdbcUrl");
        String secreto = Base64.getEncoder().encodeToString("secreto-de-arranque-solo-para-test-0123456789".getBytes());

        CcisjApplication.main(new String[] {
                "--spring.main.sources=" + CapturaDeContexto.class.getName(),
                "--spring.datasource.url=" + url + (url.contains("?") ? "&" : "?") + "currentSchema=" + schema + ",public",
                "--spring.datasource.username=" + System.getProperty("testJdbcUser", "ccisj_test"),
                "--spring.datasource.password=" + System.getProperty("testJdbcPassword", "ccisj_test"),
                "--spring.flyway.schemas=" + schema,
                "--app.jwt.secret=" + secreto,
                "--server.port=0",
                "--spring.task.scheduling.pool.size=1",
                "--app.bootstrap.admin-email=admin-arranque@example.test",
                "--app.bootstrap.admin-password=Arranque-Clave1"});

        assertNotNull(contexto, "main debe dejar un contexto levantado");
        assertTrue(contexto.isActive());
        assertNotNull(contexto.getBean(SecurityFilterChain.class));
        assertNotNull(contexto.getBean(S3StorageService.class));
        JdbcTemplate jdbc = contexto.getBean(JdbcTemplate.class);
        assertEquals(1, jdbc.queryForObject(
                "SELECT count(*) FROM usuarios WHERE email = 'admin-arranque@example.test' AND rol = 'ADMIN'", Integer.class),
                "BootstrapUsers crea el admin configurado al arrancar");

        String puerto = contexto.getEnvironment().getProperty("local.server.port");
        HttpClient http = HttpClient.newHttpClient();
        HttpResponse<String> privado = http.send(HttpRequest.newBuilder(
                URI.create("http://127.0.0.1:" + puerto + "/api/auth/me")).GET().build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(401, privado.statusCode());
        assertTrue(privado.body().contains("\"detail\":\"No autenticado\""));
        assertEquals("nosniff", privado.headers().firstValue("X-Content-Type-Options").orElse(null));
    }
}

package uy.ccisj.api.cajanegra;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import uy.ccisj.api.storage.S3StorageService;
import uy.ccisj.api.support.DatabaseTestConfig;

/**
 * Base de las pruebas de CAJA NEGRA de la API.
 *
 * <p>Se levanta la aplicación completa (Spring Boot + Flyway + seguridad real) sobre un PostgreSQL
 * de prueba, y las pruebas solo interactúan por HTTP, como lo haría un cliente externo: no llaman
 * clases internas ni consultan la base. Los casos se diseñan desde los requerimientos y contratos
 * (particiones de equivalencia, valores límite, tablas de decisión y transición de estados),
 * no desde el código. Ver {@code testing-caja-negra.md}.
 *
 * <p>Lo único reemplazado es S3 (servicio externo de AWS): se simula para no subir archivos reales.
 * Se ejecuta solo si se pasa {@code -DtestJdbcUrl=...}, igual que el resto de pruebas con base.
 */
@EnabledIfSystemProperty(named = "testJdbcUrl", matches = ".+")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
abstract class ApiCajaNegraBase {
    static final String ADMIN_EMAIL = "admin.cajanegra@example.test";
    static final String ADMIN_PASSWORD = "Admin-CajaNegra-1";
    static final String CLAVE_VALIDA = "Clave-Valida-1";
    static final ObjectMapper JSON = new ObjectMapper();
    private static final String SCHEMA = "cajanegra_" + UUID.randomUUID().toString().replace("-", "");
    private static final AtomicInteger SECUENCIA = new AtomicInteger();
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    @LocalServerPort
    int port;

    @MockBean
    S3StorageService almacenamiento;

    @DynamicPropertySource
    static void configurar(DynamicPropertyRegistry registry) {
        String url = System.getProperty("testJdbcUrl");
        new JdbcTemplate(DatabaseTestConfig.dataSource("public"))
                .execute("CREATE EXTENSION IF NOT EXISTS pgcrypto WITH SCHEMA public");
        registry.add("spring.datasource.url", () -> url + (url.contains("?") ? "&" : "?") + "currentSchema=" + SCHEMA + ",public");
        registry.add("spring.datasource.username", () -> System.getProperty("testJdbcUser", "ccisj_test"));
        registry.add("spring.datasource.password", () -> System.getProperty("testJdbcPassword", "ccisj_test"));
        registry.add("spring.flyway.schemas", () -> SCHEMA);
        registry.add("app.jwt.secret", () -> "Y2FqYS1uZWdyYS1jY2lzai1zZWNyZXRvLWRlLXBydWViYXMtMzItYnl0ZXM=");
        registry.add("app.bootstrap.admin-email", () -> ADMIN_EMAIL);
        registry.add("app.bootstrap.admin-password", () -> ADMIN_PASSWORD);
        registry.add("app.storage.bucket", () -> "bucket-de-prueba");
        // Sin SMTP: los correos se omiten (comportamiento documentado de la app sin Gmail configurado).
        registry.add("spring.mail.username", () -> "");
        // El health real incluye el SMTP de Gmail; en pruebas no hay servidor de correo.
        registry.add("management.health.mail.enabled", () -> "false");
    }

    @BeforeEach
    void simularS3() {
        when(almacenamiento.presignedDownloadUrl(any(), any())).thenReturn("https://s3.prueba/cv-firmado.pdf");
    }

    // ---------- Cliente HTTP ----------

    record Respuesta(int status, JsonNode cuerpo) {
        String texto(String campo) {
            return cuerpo == null || cuerpo.get(campo) == null ? null : cuerpo.get(campo).asText();
        }
    }

    /** Cada pedido simula un cliente distinto para que el límite de intentos no mezcle casos. */
    private String ipCliente() {
        return "10.20." + (SECUENCIA.get() / 250) + "." + (SECUENCIA.incrementAndGet() % 250 + 1);
    }

    Respuesta enviar(String metodo, String ruta, Object cuerpo, String token) {
        return enviar(metodo, ruta, cuerpo, token, ipCliente());
    }

    Respuesta enviar(String metodo, String ruta, Object cuerpo, String token, String ip) {
        try {
            HttpRequest.Builder pedido = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api" + ruta))
                    .timeout(Duration.ofSeconds(30))
                    .header("Content-Type", "application/json")
                    .header("X-Forwarded-For", ip);
            if (token != null) pedido.header("Authorization", "Bearer " + token);
            HttpRequest.BodyPublisher body = cuerpo == null
                    ? HttpRequest.BodyPublishers.noBody()
                    : HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(cuerpo));
            pedido.method(metodo, body);
            return leer(HTTP.send(pedido.build(), HttpResponse.BodyHandlers.ofString()));
        } catch (IOException | InterruptedException exception) {
            throw new IllegalStateException(exception);
        }
    }

    Respuesta subirArchivo(String ruta, String token, String nombre, String tipo, byte[] contenido, Map<String, String> campos) {
        try {
            String limite = "----cajanegra" + UUID.randomUUID();
            ByteArrayOutputStream cuerpo = new ByteArrayOutputStream();
            for (Map.Entry<String, String> campo : campos.entrySet()) {
                cuerpo.write(("--" + limite + "\r\nContent-Disposition: form-data; name=\"" + campo.getKey()
                        + "\"\r\n\r\n" + campo.getValue() + "\r\n").getBytes(StandardCharsets.UTF_8));
            }
            cuerpo.write(("--" + limite + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"" + nombre
                    + "\"\r\nContent-Type: " + tipo + "\r\n\r\n").getBytes(StandardCharsets.UTF_8));
            cuerpo.write(contenido);
            cuerpo.write(("\r\n--" + limite + "--\r\n").getBytes(StandardCharsets.UTF_8));
            HttpRequest pedido = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api" + ruta))
                    .header("Content-Type", "multipart/form-data; boundary=" + limite)
                    .header("Authorization", "Bearer " + token)
                    .header("X-Forwarded-For", ipCliente())
                    .POST(HttpRequest.BodyPublishers.ofByteArray(cuerpo.toByteArray()))
                    .build();
            return leer(HTTP.send(pedido, HttpResponse.BodyHandlers.ofString()));
        } catch (IOException | InterruptedException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static Respuesta leer(HttpResponse<String> respuesta) throws IOException {
        String texto = respuesta.body();
        return new Respuesta(respuesta.statusCode(), texto == null || texto.isBlank() ? null : JSON.readTree(texto));
    }

    // ---------- Acciones de negocio (solo por la API pública) ----------

    static String unico(String prefijo) {
        return prefijo + SECUENCIA.incrementAndGet() + UUID.randomUUID().toString().substring(0, 6);
    }

    static String cedulaUnica() {
        return String.valueOf(40_000_000 + SECUENCIA.incrementAndGet() * 7 + (int) (Math.abs(UUID.randomUUID().getMostSignificantBits()) % 7));
    }

    Respuesta login(String rol, String identificador, String clave) {
        return enviar("POST", "/auth/login", Map.of("role", rol, "identifier", identificador, "password", clave), null);
    }

    String tokenAdmin() {
        Respuesta respuesta = login("ADMIN", ADMIN_EMAIL, ADMIN_PASSWORD);
        if (respuesta.status() != 200) throw new IllegalStateException("No ingresó el admin: " + respuesta);
        return respuesta.texto("token");
    }

    Map<String, Object> registroValido(String email) {
        Map<String, Object> datos = new java.util.HashMap<>();
        datos.put("fullName", "Postulante Caja Negra");
        datos.put("identityCard", cedulaUnica());
        datos.put("phone", "099123456");
        datos.put("email", email);
        datos.put("password", CLAVE_VALIDA);
        datos.put("residenceArea", "San José de Mayo");
        datos.put("profileName", "Vendedor");
        datos.put("sectors", java.util.List.of("Ventas"));
        datos.put("availability", "FULL_TIME");
        datos.put("hasVehicle", false);
        datos.put("acceptedTerms", true);
        return datos;
    }

    /** Registra un postulante y devuelve su token. */
    String registrarPostulante(String email) {
        Respuesta respuesta = enviar("POST", "/auth/register", registroValido(email), null);
        if (respuesta.status() != 201) throw new IllegalStateException("No se registró el postulante: " + respuesta);
        return respuesta.texto("token");
    }

    long primerRubroId(String tokenAdmin) {
        Respuesta rubros = enviar("GET", "/admin/socios/rubros", null, tokenAdmin);
        return rubros.cuerpo().get(0).get("id").asLong();
    }

    /** El admin da de alta un socio con cédula; devuelve la respuesta (incluye la clave provisional). */
    Respuesta crearSocio(String tokenAdmin, String cedula) {
        Map<String, Object> socio = new java.util.HashMap<>();
        socio.put("tipoIdentificador", "CEDULA");
        socio.put("identificador", cedula);
        socio.put("email", unico("socio") + "@example.test");
        socio.put("razonSocial", unico("Empresa Caja Negra "));
        socio.put("rut", cedula);
        socio.put("telefono", "43420000");
        socio.put("calle", "Asamblea");
        socio.put("numero", "123");
        socio.put("localidad", "San José de Mayo");
        socio.put("rubroId", primerRubroId(tokenAdmin));
        socio.put("esDirectivo", false);
        socio.put("fechaAniversario", "2020-05-10");
        return enviar("POST", "/admin/socios", socio, tokenAdmin);
    }

    record SocioConSesion(long id, String cedula, String token) {}

    /** Alta de socio por el admin + primer ingreso con la clave provisional + cambio de clave obligatorio. */
    SocioConSesion socioListo(String tokenAdmin) {
        String cedula = cedulaUnica();
        Respuesta alta = crearSocio(tokenAdmin, cedula);
        if (alta.status() != 201) throw new IllegalStateException("No se creó el socio: " + alta);
        Respuesta ingreso = login("SOCIO", cedula, alta.texto("passwordProvisional"));
        if (ingreso.status() != 200) throw new IllegalStateException("No ingresó el socio: " + ingreso);
        Respuesta cambio = enviar("POST", "/auth/change-password",
                Map.of("currentPassword", alta.texto("passwordProvisional"), "newPassword", CLAVE_VALIDA), ingreso.texto("token"));
        if (cambio.status() != 200) throw new IllegalStateException("No cambió la clave el socio: " + cambio);
        return new SocioConSesion(alta.cuerpo().get("id").asLong(), cedula, cambio.texto("token"));
    }

    Map<String, Object> ofertaValida(long socioId, long rubroId) {
        Map<String, Object> oferta = new java.util.HashMap<>();
        oferta.put("socioId", socioId);
        oferta.put("rubroId", rubroId);
        oferta.put("titulo", "Vendedor/a de mostrador");
        oferta.put("cargo", "Vendedor");
        oferta.put("descripcion", "Atención al público y caja.");
        oferta.put("zona", "San José de Mayo");
        oferta.put("tipoContrato", "EFECTIVO");
        oferta.put("disponibilidadHoraria", "FULL_TIME");
        oferta.put("vacantes", 1);
        oferta.put("fechaCierre", java.time.LocalDate.now().plusDays(30).toString());
        oferta.put("mostrarNombreEmpresa", true);
        return oferta;
    }

    static byte[] pdfConTexto(String texto) {
        try (PDDocument documento = new PDDocument(); ByteArrayOutputStream salida = new ByteArrayOutputStream()) {
            PDPage pagina = new PDPage();
            documento.addPage(pagina);
            try (PDPageContentStream contenido = new PDPageContentStream(documento, pagina)) {
                contenido.beginText();
                contenido.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                contenido.newLineAtOffset(50, 700);
                contenido.showText(texto);
                contenido.endText();
            }
            documento.save(salida);
            return salida.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }
}

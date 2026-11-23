package uy.ccisj.api.cajanegra;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * CN-API-09 y CN-API-10: tabla de decisión rol × área protegida (RF-19 / control de acceso del SSDLC).
 *
 * <p>Resultado esperado: 401 sin sesión, 403 con un rol que no corresponde, 2xx con el rol correcto.
 */
@DisplayName("Caja negra API — control de acceso por rol")
class ControlDeAccesoCajaNegraTest extends ApiCajaNegraBase {
    private static Map<String, String> tokens;

    @BeforeEach
    void sesiones() {
        if (tokens != null) return;
        String admin = tokenAdmin();
        tokens = Map.of(
                "ANONIMO", "",
                "ADMIN", admin,
                "SOCIO", socioListo(admin).token(),
                "POSTULANTE", registrarPostulante(unico("acceso") + "@example.test"));
    }

    @ParameterizedTest(name = "[{index}] {0} → GET {1} = {2}")
    @CsvSource({
        // rol,        ruta,                          esperado
        "ANONIMO,     /admin/socios,                 401",
        "POSTULANTE,  /admin/socios,                 403",
        "SOCIO,       /admin/socios,                 403",
        "ADMIN,       /admin/socios,                 200",
        "ANONIMO,     /admin/caja,                   401",
        "SOCIO,       /admin/caja,                   403",
        "ADMIN,       /admin/caja,                   200",
        "ANONIMO,     /socio/bolsa,                  401",
        "POSTULANTE,  /socio/bolsa,                  403",
        "ADMIN,       /socio/bolsa,                  403",
        "SOCIO,       /socio/bolsa,                  200",
        "ANONIMO,     /postulante/perfiles,          401",
        "SOCIO,       /postulante/perfiles,          403",
        "ADMIN,       /postulante/perfiles,          403",
        "POSTULANTE,  /postulante/perfiles,          200",
        "ANONIMO,     /ofertas,                      401",
        "POSTULANTE,  /ofertas,                      403",
        "SOCIO,       /ofertas,                      200",
        "ADMIN,       /ofertas,                      200",
    })
    @DisplayName("CN-API-09 Tabla de decisión rol × área")
    void tablaDeDecision(String rol, String ruta, int esperado) {
        String token = tokens.get(rol);
        Respuesta respuesta = enviar("GET", ruta, null, token.isEmpty() ? null : token);
        assertThat(respuesta.status()).isEqualTo(esperado);
    }

    @Test
    @DisplayName("CN-API-10 Rutas públicas accesibles sin sesión y respuesta de error sin detalles internos")
    void rutasPublicas() {
        // Sin SMTP disponible el servicio igual figura sano: el correo no forma parte del health.
        assertThat(enviar("GET", "/actuator/health", null, null).status()).isEqualTo(200);
        assertThat(enviar("GET", "/sectors", null, null).status()).isEqualTo(200);

        Respuesta denegado = enviar("GET", "/admin/socios", null, tokens.get("POSTULANTE"));
        assertThat(denegado.status()).isEqualTo(403);
        String cuerpo = String.valueOf(denegado.cuerpo());
        assertThat(cuerpo).doesNotContain("Exception\":\"java").doesNotContain("at uy.ccisj");
    }
}

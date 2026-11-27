package uy.ccisj.api.cajanegra;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** CN-API-18 a CN-API-21: cobros de Caja (RF-09), alta de socios (RF-06) y datos personales del postulante (RF-05). */
@DisplayName("Caja negra API — caja, socios y datos personales")
class CajaYDatosCajaNegraTest extends ApiCajaNegraBase {

    @ParameterizedTest(name = "[{index}] monto {0} → {1} ({2})")
    @CsvSource(delimiter = '|', value = {
        "-1     | 400 | partición inválida: negativo",
        "0      | 400 | límite inferior - 1 centésimo",
        "0.01   | 200 | límite inferior",
        "150.50 | 200 | partición válida típica",
        "1.005  | 400 | más de 2 decimales",
    })
    @DisplayName("CN-API-18 Registrar cobro: valores límite del monto recibido")
    void montoDelCobro(String monto, int esperado, String caso) {
        String admin = tokenAdmin();
        long socioId = crearSocio(admin, cedulaUnica()).cuerpo().get("id").asLong();
        Map<String, Object> cobro = new HashMap<>();
        cobro.put("socioId", socioId);
        cobro.put("metodoPago", "EFECTIVO");
        cobro.put("montoRecibido", new java.math.BigDecimal(monto.trim()));

        Respuesta respuesta = enviar("POST", "/admin/caja/cobros", cobro, admin);

        assertThat(respuesta.status()).as(caso).isIn(esperado, esperado == 200 ? 201 : esperado);
    }

    @Test
    @DisplayName("CN-API-19 Cobro sin método de pago o a un socio inexistente es rechazado")
    void cobroInvalido() {
        String admin = tokenAdmin();
        long socioId = crearSocio(admin, cedulaUnica()).cuerpo().get("id").asLong();
        Map<String, Object> sinMetodo = new HashMap<>(Map.of("socioId", socioId, "montoRecibido", 100));
        assertThat(enviar("POST", "/admin/caja/cobros", sinMetodo, admin).status()).isEqualTo(400);

        Map<String, Object> inexistente = Map.of("socioId", 999_999_999, "metodoPago", "EFECTIVO", "montoRecibido", 100);
        assertThat(enviar("POST", "/admin/caja/cobros", inexistente, admin).status()).isEqualTo(404);
    }

    @Test
    @DisplayName("CN-API-20 Alta de socio: datos válidos, identificador repetido y campos obligatorios faltantes")
    void altaDeSocio() {
        String admin = tokenAdmin();
        String cedula = cedulaUnica();
        Respuesta alta = crearSocio(admin, cedula);
        assertThat(alta.status()).isEqualTo(201);
        // La clave provisional generada no es adivinable a partir de los datos del socio.
        String provisional = alta.texto("passwordProvisional");
        assertThat(provisional).hasSizeGreaterThanOrEqualTo(12).doesNotContain(cedula);

        assertThat(crearSocio(admin, cedula).status()).isEqualTo(409);

        Respuesta sinDatos = enviar("POST", "/admin/socios", Map.of("tipoIdentificador", "CEDULA"), admin);
        assertThat(sinDatos.status()).isEqualTo(400);
    }

    @Test
    @DisplayName("CN-API-21 Datos personales: obligatorios vacíos → 400 y cédula de otro postulante → 409 (nunca 500)")
    void datosPersonales() {
        String primero = registrarPostulante(unico("datos1") + "@example.test");
        String segundo = registrarPostulante(unico("datos2") + "@example.test");
        String cedulaDelSegundo = enviar("GET", "/postulante/datos-personales", null, segundo).texto("cedulaIdentidad");

        Map<String, Object> valido = new HashMap<>(Map.of("nombreCompleto", "Nombre Nuevo", "cedulaIdentidad", cedulaUnica(),
                "telefono", "098000111", "zonaResidencia", "Libertad"));
        Respuesta ok = enviar("PUT", "/postulante/datos-personales", valido, primero);
        assertThat(ok.status()).isEqualTo(200);
        assertThat(ok.texto("nombreCompleto")).isEqualTo("Nombre Nuevo");

        for (String campo : new String[] {"nombreCompleto", "cedulaIdentidad", "telefono", "zonaResidencia"}) {
            Map<String, Object> vacio = new HashMap<>(valido);
            vacio.put(campo, "  ");
            assertThat(enviar("PUT", "/postulante/datos-personales", vacio, primero).status()).as(campo).isEqualTo(400);
        }

        Map<String, Object> cedulaAjena = new HashMap<>(valido);
        cedulaAjena.put("cedulaIdentidad", cedulaDelSegundo);
        assertThat(enviar("PUT", "/postulante/datos-personales", cedulaAjena, primero).status()).isEqualTo(409);
    }
}

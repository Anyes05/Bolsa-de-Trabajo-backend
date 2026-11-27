package uy.ccisj.api.cajanegra;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** CN-API-01 a CN-API-08: registro, ingreso, límite de intentos y cierre de sesión (RF-01 y RF-03). */
@DisplayName("Caja negra API — autenticación y registro (RF-01 y RF-03)")
class AutenticacionCajaNegraTest extends ApiCajaNegraBase {

    @Test
    @DisplayName("CN-API-01 Registro con todos los datos válidos crea la cuenta POSTULANTE y devuelve sesión")
    void registroValido() {
        String email = unico("alta") + "@example.test";
        Respuesta respuesta = enviar("POST", "/auth/register", registroValido(email), null);

        assertThat(respuesta.status()).isEqualTo(201);
        assertThat(respuesta.texto("token")).isNotBlank();
        assertThat(respuesta.texto("role")).isEqualTo("POSTULANTE");
        assertThat(respuesta.texto("email")).isEqualTo(email);
        // La sesión devuelta sirve para consultar el usuario actual.
        Respuesta yo = enviar("GET", "/auth/me", null, respuesta.texto("token"));
        assertThat(yo.status()).isEqualTo(200);
        assertThat(yo.texto("email")).isEqualTo(email);
    }

    @ParameterizedTest(name = "[{index}] clave ''{0}'' → {1} ({2})")
    @CsvSource(delimiter = '|', value = {
        "Aa1-aaa       | 400 | límite inferior - 1: 7 caracteres",
        "Aa1-aaaa      | 201 | límite inferior: 8 caracteres",
        "Aa1-aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa | 201 | límite superior: 72 caracteres",
        "Aa1-aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa | 400 | límite superior + 1: 73 caracteres",
        "aa1-aaaa      | 400 | partición inválida: sin mayúscula",
        "AA1-AAAA      | 400 | partición inválida: sin minúscula",
        "Aaa-aaaa      | 400 | partición inválida: sin número",
        "Aa1aaaaa      | 400 | partición inválida: sin carácter especial",
    })
    @DisplayName("CN-API-02 Política de contraseña: valores límite de longitud y particiones de composición")
    void politicaDeClave(String clave, int esperado, String caso) {
        Map<String, Object> datos = registroValido(unico("clave") + "@example.test");
        datos.put("password", clave.trim());

        Respuesta respuesta = enviar("POST", "/auth/register", datos, null);

        assertThat(respuesta.status()).as(caso).isEqualTo(esperado);
    }

    @ParameterizedTest(name = "[{index}] {0} rubros → {1}")
    @CsvSource({"0, 400", "1, 201", "4, 201", "5, 400"})
    @DisplayName("CN-API-03 Cantidad de rubros elegidos: valores límite 1..4")
    void cantidadDeRubros(int cantidad, int esperado) {
        List<String> catalogo = List.of("Ventas", "Logistica", "Administracion", "Gastronomia", "Construccion");
        Map<String, Object> datos = registroValido(unico("rubros") + "@example.test");
        datos.put("sectors", catalogo.subList(0, cantidad));

        assertThat(enviar("POST", "/auth/register", datos, null).status()).isEqualTo(esperado);
    }

    @Test
    @DisplayName("CN-API-04 Registro sin aceptar términos, con correo inválido o con correo repetido es rechazado")
    void registroInvalido() {
        Map<String, Object> sinTerminos = registroValido(unico("terminos") + "@example.test");
        sinTerminos.put("acceptedTerms", false);
        assertThat(enviar("POST", "/auth/register", sinTerminos, null).status()).isEqualTo(400);

        Map<String, Object> correoInvalido = registroValido("no-es-un-correo");
        assertThat(enviar("POST", "/auth/register", correoInvalido, null).status()).isEqualTo(400);

        String email = unico("repetido") + "@example.test";
        registrarPostulante(email);
        Respuesta repetido = enviar("POST", "/auth/register", registroValido(email), null);
        assertThat(repetido.status()).isEqualTo(409);
        // El mensaje es genérico: no confirma que el correo exista (evita enumeración de cuentas).
        assertThat(repetido.texto("detail") + repetido.texto("message")).doesNotContain(email);
    }

    @Test
    @DisplayName("CN-API-05 Ingreso: credenciales correctas, clave incorrecta, rol equivocado y campos vacíos")
    void ingreso() {
        String email = unico("ingreso") + "@example.test";
        registrarPostulante(email);

        Respuesta correcto = login("POSTULANTE", email, CLAVE_VALIDA);
        assertThat(correcto.status()).isEqualTo(200);
        assertThat(correcto.texto("token")).isNotBlank();

        assertThat(login("POSTULANTE", email, "Otra-Clave-9").status()).isEqualTo(401);
        assertThat(login("ADMIN", email, CLAVE_VALIDA).status()).isEqualTo(401);
        assertThat(login("POSTULANTE", "nadie-" + email, CLAVE_VALIDA).status()).isEqualTo(401);
        assertThat(login("POSTULANTE", "", CLAVE_VALIDA).status()).isEqualTo(400);
        assertThat(enviar("POST", "/auth/login", Map.of("identifier", email, "password", CLAVE_VALIDA), null).status())
                .isEqualTo(400);
    }

    @Test
    @DisplayName("CN-API-06 Límite de intentos: 5 fallos permitidos, el 6.º intento se bloquea con 429 (transición de estado)")
    void limiteDeIntentos() {
        String email = unico("bloqueo") + "@example.test";
        registrarPostulante(email);
        String mismaIp = "10.99.0." + (int) (Math.random() * 200 + 1);
        Map<String, Object> claveMala = Map.of("role", "POSTULANTE", "identifier", email, "password", "Mala-Clave-1");

        for (int intento = 1; intento <= 5; intento++) {
            assertThat(enviar("POST", "/auth/login", claveMala, null, mismaIp).status()).as("intento %d", intento).isEqualTo(401);
        }
        // Bloqueado: ni siquiera la clave correcta entra desde ese cliente.
        Map<String, Object> claveBuena = Map.of("role", "POSTULANTE", "identifier", email, "password", CLAVE_VALIDA);
        assertThat(enviar("POST", "/auth/login", claveMala, null, mismaIp).status()).isEqualTo(429);
        assertThat(enviar("POST", "/auth/login", claveBuena, null, mismaIp).status()).isEqualTo(429);
    }

    @Test
    @DisplayName("CN-API-06b Cambiar X-Forwarded-For en cada intento no evita el bloqueo (corrección del hallazgo 1)")
    void forwardedForFalsoNoSalteaElLimite() {
        String email = unico("bypass") + "@example.test";
        registrarPostulante(email);
        String mismaIp = "10.98.0." + (int) (Math.random() * 200 + 1);
        Map<String, Object> claveMala = Map.of("role", "POSTULANTE", "identifier", email, "password", "Mala-Clave-1");

        for (int intento = 1; intento <= 5; intento++) {
            String falso = "203.0.113." + intento;
            assertThat(enviar("POST", "/auth/login", claveMala, null, mismaIp, falso).status()).isEqualTo(401);
        }
        assertThat(enviar("POST", "/auth/login", claveMala, null, mismaIp, "198.51.100.77").status()).isEqualTo(429);
    }

    @Test
    @DisplayName("CN-API-07 Cerrar sesión invalida el token en el servidor (transición sesión activa → cerrada)")
    void cierreDeSesion() {
        String token = registrarPostulante(unico("logout") + "@example.test");
        assertThat(enviar("GET", "/auth/me", null, token).status()).isEqualTo(200);

        assertThat(enviar("POST", "/auth/logout", null, token).status()).isBetween(200, 204);

        assertThat(enviar("GET", "/auth/me", null, token).status()).isEqualTo(401);
    }

    @Test
    @DisplayName("CN-API-08 Token ausente, mal formado o alterado no da acceso")
    void tokenInvalido() {
        String token = registrarPostulante(unico("token") + "@example.test");
        assertThat(enviar("GET", "/auth/me", null, null).status()).isEqualTo(401);
        assertThat(enviar("GET", "/auth/me", null, "no-es-un-jwt").status()).isEqualTo(401);
        String alterado = token.substring(0, token.length() - 4) + (token.endsWith("AAAA") ? "BBBB" : "AAAA");
        assertThat(enviar("GET", "/auth/me", null, alterado).status()).isEqualTo(401);
    }
}

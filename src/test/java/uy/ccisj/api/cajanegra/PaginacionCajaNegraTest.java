package uy.ccisj.api.cajanegra;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** CN-API-22 a CN-API-27: búsqueda, filtros y paginación en el servidor de los listados. */
@DisplayName("Caja negra API — listados paginados")
class PaginacionCajaNegraTest extends ApiCajaNegraBase {

    @ParameterizedTest(name = "[{index}] size={0} → {1}")
    @CsvSource({"0, 400", "1, 200", "100, 200", "101, 400"})
    @DisplayName("CN-API-22 Tamaño de página: valores límite 1..100")
    void tamanoDePagina(int size, int esperado) {
        assertThat(enviar("GET", "/admin/socios/pagina?size=" + size, null, tokenAdmin()).status()).isEqualTo(esperado);
    }

    @Test
    @DisplayName("CN-API-23 Página negativa, búsqueda de 101 caracteres y parámetros con tipo inválido → 400")
    void parametrosInvalidos() {
        String admin = tokenAdmin();
        assertThat(enviar("GET", "/admin/socios/pagina?page=-1", null, admin).status()).isEqualTo(400);
        assertThat(enviar("GET", "/admin/socios/pagina?q=" + "a".repeat(100), null, admin).status()).isEqualTo(200);
        assertThat(enviar("GET", "/admin/socios/pagina?q=" + "a".repeat(101), null, admin).status()).isEqualTo(400);
        Respuesta tipoInvalido = enviar("GET", "/admin/socios/pagina?page=uno", null, admin);
        assertThat(tipoInvalido.status()).isEqualTo(400);
        // El mensaje nombra el parámetro pero no repite el valor recibido.
        assertThat(String.valueOf(tipoInvalido.cuerpo())).contains("page").doesNotContain("uno");
        assertThat(enviar("GET", "/admin/postulantes?estado=CUALQUIERA", null, admin).status()).isEqualTo(400);
        assertThat(enviar("GET", "/admin/postulantes?libreta=quizas", null, admin).status()).isEqualTo(400);
    }

    @Test
    @DisplayName("CN-API-24 Socios: la búsqueda encuentra al socio y una página fuera de rango viene vacía")
    void paginaDeSocios() {
        String admin = tokenAdmin();
        Respuesta alta = crearSocio(admin, cedulaUnica());
        String razonSocial = alta.texto("razonSocial");

        JsonNode encontrado = enviar("GET", "/admin/socios/pagina?q=" + razonSocial.replace(" ", "%20") + "&size=5", null, admin).cuerpo();
        assertThat(encontrado.get("totalElements").asLong()).isEqualTo(1);
        assertThat(encontrado.get("content").get(0).get("razonSocial").asText()).isEqualTo(razonSocial);

        JsonNode fueraDeRango = enviar("GET", "/admin/socios/pagina?page=9999", null, admin).cuerpo();
        assertThat(fueraDeRango.get("content")).isEmpty();
        assertThat(fueraDeRango.get("totalElements").asLong()).isPositive();
    }

    @Test
    @DisplayName("CN-API-25 Postulantes (admin): filtro por estado y búsqueda por nombre, paginados por postulante")
    void postulantesDelAdmin() {
        String admin = tokenAdmin();
        String email = unico("pagina") + "@example.test";
        registrarPostulante(email);

        for (String estado : new String[] {"TODOS", "ACTIVO", "INACTIVO"}) {
            Respuesta r = enviar("GET", "/admin/postulantes?estado=" + estado, null, admin);
            assertThat(r.status()).as(estado).isEqualTo(200);
        }
        JsonNode buscado = enviar("GET", "/admin/postulantes?q=" + email + "&size=1", null, admin).cuerpo();
        assertThat(buscado.get("resultado").get("perfiles").asLong()).isEqualTo(1);
        assertThat(buscado.get("resultado").get("pagina").get("content")).hasSize(1);
        assertThat(buscado.get("activos").asLong() + buscado.get("inactivos").asLong()).isPositive();

        JsonNode inactivos = enviar("GET", "/admin/postulantes?q=" + email + "&estado=INACTIVO", null, admin).cuerpo();
        assertThat(inactivos.get("resultado").get("perfiles").asLong()).isZero();
    }

    @Test
    @DisplayName("CN-API-26 Bolsa del socio: orden por nombre y filtros por rubro, disponibilidad y libreta")
    void bolsaDelSocio() {
        String admin = tokenAdmin();
        SocioConSesion socio = socioListo(admin);
        registrarPostulante(unico("bolsa-pag") + "@example.test");

        for (String consulta : new String[] {"?orden=NOMBRE", "?orden=RECIENTES&rubro=Ventas", "?disponibilidad=FULL_TIME&libreta=no", "?libreta=yes&size=1"}) {
            Respuesta r = enviar("GET", "/socio/bolsa" + consulta, null, socio.token());
            assertThat(r.status()).as(consulta).isEqualTo(200);
            assertThat(r.cuerpo().get("pagina").get("content").isArray()).isTrue();
        }
        assertThat(enviar("GET", "/socio/bolsa?orden=ALEATORIO", null, socio.token()).status()).isEqualTo(400);
    }

    @Test
    @DisplayName("CN-API-27 Caja: exportación con búsqueda y oferta individual con el alcance de cada rol")
    void exportacionYOfertaPorId() {
        String admin = tokenAdmin();
        Respuesta socio = crearSocio(admin, cedulaUnica());
        String busqueda = "/admin/caja/cuentas/exportacion?q=" + socio.texto("razonSocial").replace(" ", "%20");
        // Sin estados elegidos no se exporta nada (partición vacía); con todos, aparece el socio buscado.
        Respuesta sinEstados = enviar("GET", busqueda, null, admin);
        assertThat(sinEstados.status()).isEqualTo(200);
        assertThat(sinEstados.cuerpo()).isEmpty();
        Respuesta exportacion = enviar("GET", busqueda + "&estados=AL_DIA&estados=MOROSO&estados=INACTIVO", null, admin);
        assertThat(exportacion.status()).isEqualTo(200);
        assertThat(exportacion.cuerpo()).hasSize(1);

        SocioConSesion duenio = socioListo(admin);
        long rubroId = enviar("GET", "/ofertas/rubros", null, admin).cuerpo().get(0).get("id").asLong();
        long ofertaId = enviar("POST", "/ofertas", ofertaValida(duenio.id(), rubroId), admin).cuerpo().get("id").asLong();
        assertThat(enviar("GET", "/ofertas/" + ofertaId, null, admin).texto("titulo")).isEqualTo("Vendedor/a de mostrador");
        assertThat(enviar("GET", "/ofertas/" + ofertaId, null, duenio.token()).status()).isEqualTo(200);
        SocioConSesion otro = socioListo(admin);
        assertThat(enviar("GET", "/ofertas/" + ofertaId, null, otro.token()).status()).isEqualTo(404);
        assertThat(enviar("GET", "/ofertas/" + ofertaId, null, null).status()).isEqualTo(401);
    }
}

package uy.ccisj.api.cajanegra;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** CN-API-11 a CN-API-16: ofertas laborales y postulaciones (RF-11), de punta a punta por HTTP. */
@DisplayName("Caja negra API — bolsa de empleo y postulaciones (RF-11)")
class BolsaDeEmpleoCajaNegraTest extends ApiCajaNegraBase {

    private record Escenario(String admin, SocioConSesion socio, long ofertaId, String postulante, long perfilId) {}

    /** Admin publica una oferta para un socio; un postulante tiene un perfil visible con CV activo. */
    private Escenario escenario() {
        String admin = tokenAdmin();
        SocioConSesion socio = socioListo(admin);
        Respuesta rubros = enviar("GET", "/ofertas/rubros", null, admin);
        long rubroId = rubros.cuerpo().get(0).get("id").asLong();
        Respuesta oferta = enviar("POST", "/ofertas", ofertaValida(socio.id(), rubroId), admin);
        assertThat(oferta.status()).as("alta de oferta %s", oferta).isEqualTo(201);

        String postulante = registrarPostulante(unico("bolsa") + "@example.test");
        JsonNode perfiles = enviar("GET", "/postulante/perfiles", null, postulante).cuerpo();
        long perfilId = perfiles.get(0).get("id").asLong();
        Respuesta cv = subirArchivo("/postulante/cvs", postulante, "cv.pdf", "application/pdf",
                pdfConTexto("Curriculum de prueba"), Map.of("perfilLaboralId", String.valueOf(perfilId)));
        assertThat(cv.status()).as("subida de CV %s", cv).isEqualTo(201);
        return new Escenario(admin, socio, oferta.cuerpo().get("id").asLong(), postulante, perfilId);
    }

    @Test
    @DisplayName("CN-API-11 Flujo completo: publicar, postularse, el socio la ve como RECIBIDA y descarga el CV")
    void flujoCompleto() {
        Escenario e = escenario();

        Respuesta visibles = enviar("GET", "/postulante/ofertas", null, e.postulante());
        assertThat(visibles.status()).isEqualTo(200);
        assertThat(visibles.cuerpo().findValuesAsText("id")).contains(String.valueOf(e.ofertaId()));

        Respuesta postulacion = enviar("POST", "/postulante/ofertas/" + e.ofertaId() + "/postulaciones",
                Map.of("perfilId", e.perfilId()), e.postulante());
        assertThat(postulacion.status()).isEqualTo(201);

        Respuesta mias = enviar("GET", "/postulante/ofertas/postulaciones", null, e.postulante());
        assertThat(mias.cuerpo()).hasSize(1);

        Respuesta recibidas = enviar("GET", "/socio/ofertas/" + e.ofertaId() + "/postulaciones", null, e.socio().token());
        assertThat(recibidas.status()).isEqualTo(200);
        JsonNode recibida = recibidas.cuerpo().get(0);
        assertThat(recibida.get("status").asText()).isEqualTo("RECIBIDA");
        // La bolsa no expone correo ni teléfono del postulante al socio.
        assertThat(recibida.toString()).doesNotContain("@example.test");

        Respuesta cv = enviar("GET", "/socio/postulaciones/" + recibida.get("id").asLong() + "/cv", null, e.socio().token());
        assertThat(cv.status()).isEqualTo(200);
        assertThat(cv.texto("url")).isEqualTo("https://s3.prueba/cv-firmado.pdf");
    }

    @Test
    @DisplayName("CN-API-12 No se permite postularse dos veces a la misma oferta")
    void postulacionDuplicada() {
        Escenario e = escenario();
        String ruta = "/postulante/ofertas/" + e.ofertaId() + "/postulaciones";
        assertThat(enviar("POST", ruta, Map.of("perfilId", e.perfilId()), e.postulante()).status()).isEqualTo(201);
        assertThat(enviar("POST", ruta, Map.of("perfilId", e.perfilId()), e.postulante()).status()).isEqualTo(409);
    }

    @Test
    @DisplayName("CN-API-13 Oferta cerrada no recibe postulaciones y una cerrada no se reabre")
    void ofertaCerrada() {
        Escenario e = escenario();
        Respuesta cierre = enviar("PATCH", "/ofertas/" + e.ofertaId() + "/estado?estado=CERRADA", null, e.admin());
        assertThat(cierre.status()).isEqualTo(200);

        assertThat(enviar("POST", "/postulante/ofertas/" + e.ofertaId() + "/postulaciones",
                Map.of("perfilId", e.perfilId()), e.postulante()).status()).isEqualTo(409);
        assertThat(enviar("PATCH", "/ofertas/" + e.ofertaId() + "/estado?estado=ACTIVA", null, e.admin()).status())
                .isEqualTo(409);
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @CsvSource(delimiter = '|', value = {
        "RECIBIDA → REVISADA → CONTACTADA → SELECCIONADA (camino válido completo) | REVISADA,CONTACTADA,SELECCIONADA | 200,200,200",
        "RECIBIDA → DESCARTADA → REVISADA (no sale de un estado final)            | DESCARTADA,REVISADA             | 200,409",
        "RECIBIDA → CONTACTADA (salta REVISADA)                                   | CONTACTADA                       | 409",
        "RECIBIDA → SELECCIONADA → DESCARTADA (estado final)                       | SELECCIONADA                     | 409",
        "RECIBIDA → REVISADA → RECIBIDA (no vuelve atrás)                          | REVISADA,RECIBIDA                | 200,409",
    })
    @DisplayName("CN-API-14 Transición de estados de una postulación gestionada por el socio")
    void transicionesDeEstado(String caso, String estados, String esperados) {
        Escenario e = escenario();
        enviar("POST", "/postulante/ofertas/" + e.ofertaId() + "/postulaciones", Map.of("perfilId", e.perfilId()), e.postulante());
        long postulacionId = enviar("GET", "/socio/ofertas/" + e.ofertaId() + "/postulaciones", null, e.socio().token())
                .cuerpo().get(0).get("id").asLong();

        String[] pasos = estados.trim().split(",");
        String[] codigos = esperados.trim().split(",");
        for (int i = 0; i < pasos.length; i++) {
            Respuesta r = enviar("PATCH", "/socio/postulaciones/" + postulacionId + "/estado?estado=" + pasos[i], null, e.socio().token());
            assertThat(r.status()).as("%s: paso %s", caso, pasos[i]).isEqualTo(Integer.parseInt(codigos[i]));
        }
    }

    @Test
    @DisplayName("CN-API-15 Un socio no ve ni gestiona postulaciones de ofertas de otro socio")
    void aislamientoEntreSocios() {
        Escenario e = escenario();
        enviar("POST", "/postulante/ofertas/" + e.ofertaId() + "/postulaciones", Map.of("perfilId", e.perfilId()), e.postulante());
        long postulacionId = enviar("GET", "/socio/ofertas/" + e.ofertaId() + "/postulaciones", null, e.socio().token())
                .cuerpo().get(0).get("id").asLong();
        SocioConSesion otro = socioListo(e.admin());

        // La consulta responde 200 pero vacía: no revela ninguna postulación de la oferta ajena.
        Respuesta ajenas = enviar("GET", "/socio/ofertas/" + e.ofertaId() + "/postulaciones", null, otro.token());
        assertThat(ajenas.status()).isEqualTo(200);
        assertThat(ajenas.cuerpo()).isEmpty();
        assertThat(enviar("PATCH", "/socio/postulaciones/" + postulacionId + "/estado?estado=REVISADA", null, otro.token()).status())
                .isEqualTo(404);
        assertThat(enviar("GET", "/socio/postulaciones/" + postulacionId + "/cv", null, otro.token()).status())
                .isIn(403, 404);
    }

    @ParameterizedTest(name = "[{index}] {0} → {2}")
    @CsvSource(delimiter = '|', value = {
        "vacantes = 0 (límite inferior - 1)     | vacantes     | 0          | 400",
        "vacantes = 1 (límite inferior)         | vacantes     | 1          | 201",
        "fecha de cierre ayer (pasado)          | fechaCierre  | AYER       | 400",
        "fecha de cierre hoy (límite)           | fechaCierre  | HOY        | 201",
        "título vacío                           | titulo       | ''         | 400",
        "título de 180 caracteres (límite)      | titulo       | T180       | 201",
        "título de 181 caracteres (límite + 1)  | titulo       | T181       | 400",
        "descripción vacía                      | descripcion  | ''         | 400",
    })
    @DisplayName("CN-API-16 Validación de ofertas: valores límite y particiones inválidas")
    void validacionDeOfertas(String caso, String campo, String valor, int esperado) {
        String admin = tokenAdmin();
        SocioConSesion socio = socioListo(admin);
        long rubroId = enviar("GET", "/ofertas/rubros", null, admin).cuerpo().get(0).get("id").asLong();
        Map<String, Object> oferta = ofertaValida(socio.id(), rubroId);
        Object dato = switch (valor) {
            case "AYER" -> LocalDate.now().minusDays(1).toString();
            case "HOY" -> LocalDate.now().toString();
            case "T180" -> "T".repeat(180);
            case "T181" -> "T".repeat(181);
            default -> campo.equals("vacantes") ? (Object) Integer.parseInt(valor) : valor;
        };
        oferta.put(campo, dato);

        assertThat(enviar("POST", "/ofertas", oferta, socio.token()).status()).as(caso).isEqualTo(esperado);
    }

    @Test
    @DisplayName("CN-API-17 Un archivo que dice ser PDF pero no lo es se rechaza")
    void cvConFirmaFalsa() {
        String postulante = registrarPostulante(unico("cvfalso") + "@example.test");
        long perfilId = enviar("GET", "/postulante/perfiles", null, postulante).cuerpo().get(0).get("id").asLong();

        Respuesta falso = subirArchivo("/postulante/cvs", postulante, "cv.pdf", "application/pdf",
                "esto es un ejecutable disfrazado".getBytes(), Map.of("perfilLaboralId", String.valueOf(perfilId)));

        assertThat(falso.status()).isEqualTo(400);
        assertThat(enviar("GET", "/postulante/cvs", null, postulante).cuerpo()).isEmpty();
        assertThat(List.of(falso.status())).doesNotContain(500);
    }
}

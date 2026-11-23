package uy.ccisj.api.oferta;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.server.ResponseStatusException;
import uy.ccisj.api.catalogo.RubroEmpleoRepository;
import uy.ccisj.api.domain.EstadoOferta;
import uy.ccisj.api.socio.Socio;
import uy.ccisj.api.socio.SocioRepository;
import uy.ccisj.api.user.User;

class OfertaEmpleoControllerCoverageTest {
    private final OfertaEmpleoService service = mock(OfertaEmpleoService.class);
    private final SocioRepository socios = mock(SocioRepository.class);
    private final RubroEmpleoRepository rubros = mock(RubroEmpleoRepository.class);
    private final OfertaEmpleoController controller = new OfertaEmpleoController(service, socios, rubros);

    @Test
    void socioListaSusOfertasYAdminCreaYCambiaEstado() {
        OfertaRequest request = mock(OfertaRequest.class);

        controller.list(auth("socio@x.test", "ROLE_SOCIO"));
        controller.create(auth("admin@x.test", "ROLE_ADMIN"), request);
        controller.update(auth("socio@x.test", "ROLE_SOCIO"), 3L, request);
        controller.setStatus(auth("admin@x.test", "ROLE_ADMIN"), 3L, EstadoOferta.CERRADA);

        verify(service).list("socio@x.test", false);
        verify(service).create("admin@x.test", true, request);
        verify(service).update("socio@x.test", false, 3L, request);
        verify(service).setStatus("admin@x.test", true, 3L, EstadoOferta.CERRADA);
    }

    @Test
    void rechazaPostulantesYAnonimosEnCadaOperacion() {
        Authentication postulante = auth("p@x.test", "ROLE_POSTULANTE");

        assertForbidden(() -> controller.list(postulante));
        assertForbidden(() -> controller.create(null, mock(OfertaRequest.class)));
        assertForbidden(() -> controller.update(null, 1L, mock(OfertaRequest.class)));
        assertForbidden(() -> controller.setStatus(postulante, 1L, EstadoOferta.PAUSADA));
        assertForbidden(() -> controller.activeSocios(null));
        verifyNoInteractions(service);
    }

    @Test
    void paginaFiltraPorTextoYEstadoEnServidorRespetandoElAlcanceDelRol() {
        OfertaResponse vendedor = oferta(1L, "Vendedor", "Tienda Centro", "Ventas", EstadoOferta.ACTIVA);
        OfertaResponse cocinero = oferta(2L, "Cocinero", "Parrilla Sur", "Gastronomía", EstadoOferta.PAUSADA);
        OfertaResponse repartidor = oferta(3L, "Repartidor", "Tienda Centro", "Logística", EstadoOferta.ACTIVA);
        when(service.list("socio@x.test", false)).thenReturn(List.of(vendedor, cocinero, repartidor));
        Authentication socio = auth("socio@x.test", "ROLE_SOCIO");

        assertThat(controller.page(socio, "tienda", null, 0, 20).content()).containsExactly(vendedor, repartidor);
        assertThat(controller.page(socio, null, EstadoOferta.PAUSADA, 0, 20).content()).containsExactly(cocinero);
        assertThat(controller.page(socio, "gastronomia", null, 0, 20).content()).containsExactly(cocinero);
        var pagina = controller.page(socio, null, null, 1, 2);
        assertThat(pagina.content()).containsExactly(repartidor);
        assertThat(pagina.totalElements()).isEqualTo(3);
        assertThat(pagina.totalPages()).isEqualTo(2);
        verify(service, org.mockito.Mockito.times(4)).list("socio@x.test", false);

        assertForbidden(() -> controller.page(auth("p@x.test", "ROLE_POSTULANTE"), null, null, null, null));
    }

    @Test
    void obtieneUnaOfertaPorIdConElAlcanceDelRol() {
        OfertaResponse oferta = oferta(7L, "Vendedor", "Tienda", "Ventas", EstadoOferta.ACTIVA);
        when(service.get("socio@x.test", false, 7L)).thenReturn(oferta);
        assertThat(controller.get(auth("socio@x.test", "ROLE_SOCIO"), 7L)).isSameAs(oferta);
        assertForbidden(() -> controller.get(auth("p@x.test", "ROLE_POSTULANTE"), 7L));
    }

    private static OfertaResponse oferta(Long id, String titulo, String socio, String rubro, EstadoOferta estado) {
        return new OfertaResponse(id, 1L, socio, 1L, rubro, titulo, titulo, "", "", "", "", null, null, 1,
                null, null, estado, 0, true);
    }

    @Test
    void sociosActivosExcluyeSociosSinUsuarioOInactivos() {
        Socio sinUsuario = mock(Socio.class);
        Socio inactivo = socio(2L, "Inactiva", false);
        Socio activo = socio(3L, "Activa", true);
        when(socios.findAllByOrderByRazonSocialAsc()).thenReturn(List.of(sinUsuario, inactivo, activo));

        assertThat(controller.activeSocios(auth("admin@x.test", "ROLE_ADMIN")))
                .containsExactly(new OfertaEmpleoController.SocioOption(3L, "Activa"));
    }

    private static Socio socio(Long id, String nombre, boolean activo) {
        User usuario = mock(User.class);
        when(usuario.isActivo()).thenReturn(activo);
        Socio socio = mock(Socio.class);
        when(socio.getId()).thenReturn(id);
        when(socio.getRazonSocial()).thenReturn(nombre);
        when(socio.getUsuario()).thenReturn(usuario);
        return socio;
    }

    private static Authentication auth(String email, String... roles) {
        return new UsernamePasswordAuthenticationToken(email, null,
                Arrays.stream(roles).map(SimpleGrantedAuthority::new).toList());
    }

    private static void assertForbidden(Runnable accion) {
        assertThatThrownBy(accion::run)
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(error -> assertThat(((ResponseStatusException) error).getStatusCode())
                        .isEqualTo(HttpStatus.FORBIDDEN));
    }
}

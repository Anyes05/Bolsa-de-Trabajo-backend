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

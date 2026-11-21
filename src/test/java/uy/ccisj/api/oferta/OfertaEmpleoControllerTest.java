package uy.ccisj.api.oferta;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.server.ResponseStatusException;
import uy.ccisj.api.catalogo.RubroEmpleo;
import uy.ccisj.api.catalogo.RubroEmpleoRepository;
import uy.ccisj.api.domain.EstadoOferta;
import uy.ccisj.api.socio.Socio;
import uy.ccisj.api.socio.SocioRepository;
import uy.ccisj.api.user.User;

class OfertaEmpleoControllerTest {
    private final OfertaEmpleoService service = mock(OfertaEmpleoService.class);
    private final SocioRepository socios = mock(SocioRepository.class);
    private final RubroEmpleoRepository rubros = mock(RubroEmpleoRepository.class);
    private OfertaEmpleoController controller;

    @BeforeEach
    void setUp() {
        controller = new OfertaEmpleoController(service, socios, rubros);
    }

    @Test
    void adminYSocioDeleganOperacionesConSuIdentidad() {
        var admin = auth("admin@example.test", "ROLE_ADMIN");
        var socio = auth("socio@example.test", "ROLE_SOCIO");
        OfertaRequest request = mock(OfertaRequest.class);

        controller.list(admin);
        controller.create(socio, request);
        controller.update(admin, 4L, request);
        controller.setStatus(socio, 4L, EstadoOferta.PAUSADA);

        verify(service).list("admin@example.test", true);
        verify(service).create("socio@example.test", false, request);
        verify(service).update("admin@example.test", true, 4L, request);
        verify(service).setStatus("socio@example.test", false, 4L, EstadoOferta.PAUSADA);
    }

    @Test
    void rechazaAnonimoYPostulanteEnOperacionesProtegidas() {
        assertForbidden(() -> controller.list(null));
        assertForbidden(() -> controller.create(auth("persona@example.test", "ROLE_POSTULANTE"), mock(OfertaRequest.class)));
        assertForbidden(() -> controller.update(auth("persona@example.test", "ROLE_POSTULANTE"), 1L, mock(OfertaRequest.class)));
        assertForbidden(() -> controller.setStatus(null, 1L, EstadoOferta.ACTIVA));
        assertForbidden(() -> controller.activeSocios(auth("socio@example.test", "ROLE_SOCIO")));
    }

    @Test
    void listaSoloSociosActivosYRubrosActivos() {
        Socio active = mock(Socio.class);
        Socio inactive = mock(Socio.class);
        User activeUser = mock(User.class);
        User inactiveUser = mock(User.class);
        when(activeUser.isActivo()).thenReturn(true);
        when(inactiveUser.isActivo()).thenReturn(false);
        when(active.getUsuario()).thenReturn(activeUser);
        when(inactive.getUsuario()).thenReturn(inactiveUser);
        when(active.getId()).thenReturn(1L);
        when(active.getRazonSocial()).thenReturn("Empresa activa");
        when(socios.findAllByOrderByRazonSocialAsc()).thenReturn(List.of(active, inactive));
        RubroEmpleo rubro = mock(RubroEmpleo.class);
        when(rubro.getId()).thenReturn(2L);
        when(rubro.getNombreRubro()).thenReturn("Tecnología");
        when(rubros.findByActivoTrueOrderByNombreRubroAsc()).thenReturn(List.of(rubro));

        assertEquals(List.of("Empresa activa"), controller.activeSocios(auth("admin@example.test", "ROLE_ADMIN"))
                .stream().map(OfertaEmpleoController.SocioOption::razonSocial).toList());
        assertEquals("Tecnología", controller.activeRubros().getFirst().nombreRubro());
    }

    private UsernamePasswordAuthenticationToken auth(String email, String role) {
        return new UsernamePasswordAuthenticationToken(email, null, List.of(new SimpleGrantedAuthority(role)));
    }

    private void assertForbidden(Runnable action) {
        assertEquals(HttpStatus.FORBIDDEN,
                assertThrows(ResponseStatusException.class, action::run).getStatusCode());
    }
}

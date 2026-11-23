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

class PostulanteOfertaControllerCoverageTest {
    private final PostulacionService postulaciones = mock(PostulacionService.class);
    private final OfertaEmpleoService ofertas = mock(OfertaEmpleoService.class);
    private final PostulanteOfertaController controller = new PostulanteOfertaController(postulaciones, ofertas);

    @Test
    void postulanteListaOfertasRubrosYPostulacionesPropias() {
        Authentication postulante = auth("ana@example.test", "ROLE_POSTULANTE");
        OfertaResponse oferta = mock(OfertaResponse.class);
        var rubro = new OfertaEmpleoService.RubroOptionResponse(3L, "Ventas");
        PostulacionService.PostulacionResponse propia = mock(PostulacionService.PostulacionResponse.class);
        when(postulaciones.listOffers(3L)).thenReturn(List.of(oferta));
        when(ofertas.listOfferSectors()).thenReturn(List.of(rubro));
        when(postulaciones.listMine("ana@example.test")).thenReturn(List.of(propia));

        assertThat(controller.list(postulante, 3L)).containsExactly(oferta);
        assertThat(controller.sectors(postulante)).containsExactly(rubro);
        assertThat(controller.mine(postulante)).containsExactly(propia);
        verify(postulaciones).listOffers(3L);
        verify(postulaciones).listMine("ana@example.test");

        var pagina = controller.page(postulante, 3L, 0, 10);
        assertThat(pagina.content()).containsExactly(oferta);
        assertThat(pagina.totalElements()).isEqualTo(1);
    }

    @Test
    void paginaDeOfertasRechazaOtrosRolesYParametrosInvalidos() {
        assertForbidden(() -> controller.page(auth("socio@example.test", "ROLE_SOCIO"), null, null, null));
        assertThatThrownBy(() -> controller.page(auth("ana@example.test", "ROLE_POSTULANTE"), null, -1, 10))
                .isInstanceOf(ResponseStatusException.class);
        verifyNoInteractions(postulaciones);
    }

    @Test
    void postulantePostulaConElPerfilIndicado() {
        Authentication postulante = auth("ana@example.test", "ROLE_USER", "ROLE_POSTULANTE");
        PostulacionService.PostulacionResponse creada = mock(PostulacionService.PostulacionResponse.class);
        when(postulaciones.apply("ana@example.test", 7L, 11L)).thenReturn(creada);

        var request = new PostulanteOfertaController.ApplyRequest(11L);

        assertThat(request.perfilId()).isEqualTo(11L);
        assertThat(controller.apply(postulante, 7L, request)).isSameAs(creada);
    }

    @Test
    void rechazaAnonimoYOtrosRolesSinTocarServicios() {
        Authentication socio = auth("socio@example.test", "ROLE_SOCIO");

        assertForbidden(() -> controller.list(null, null));
        assertForbidden(() -> controller.sectors(socio));
        assertForbidden(() -> controller.mine(auth("admin@example.test", "ROLE_ADMIN")));
        assertForbidden(() -> controller.apply(socio, 1L, new PostulanteOfertaController.ApplyRequest(1L)));
        verifyNoInteractions(postulaciones, ofertas);
    }

    private static Authentication auth(String email, String... roles) {
        return new UsernamePasswordAuthenticationToken(email, null,
                Arrays.stream(roles).map(SimpleGrantedAuthority::new).toList());
    }

    private static void assertForbidden(Runnable action) {
        assertThatThrownBy(action::run)
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Solo los postulantes")
                .satisfies(error -> assertThat(((ResponseStatusException) error).getStatusCode())
                        .isEqualTo(HttpStatus.FORBIDDEN));
    }
}

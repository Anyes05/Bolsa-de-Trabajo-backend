package uy.ccisj.api.oferta;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import uy.ccisj.api.catalogo.RubroEmpleo;
import uy.ccisj.api.domain.EstadoOferta;
import uy.ccisj.api.postulante.*;

class PostulacionServiceTest {
    private final OfertaEmpleoRepository offers = mock(OfertaEmpleoRepository.class);
    private final PostulanteRepository applicants = mock(PostulanteRepository.class);
    private final PostulacionRepository applications = mock(PostulacionRepository.class);
    private final CvRepository cvs = mock(CvRepository.class);
    private final OfertaEmpleoService offerService = mock(OfertaEmpleoService.class);
    private final EntityManager em = mock(EntityManager.class);
    private final Postulante applicant = mock(Postulante.class);
    private final OfertaEmpleo offer = mock(OfertaEmpleo.class);
    private final PerfilLaboral profile = mock(PerfilLaboral.class);
    private PostulacionService service;

    @BeforeEach
    void setUp() {
        service = new PostulacionService(offers, applicants, applications, cvs, offerService, em);
        when(applicant.getId()).thenReturn(1L);
        when(applicants.findByUsuarioEmailIgnoreCase("applicant@example.test")).thenReturn(Optional.of(applicant));
        when(offers.findById(2L)).thenReturn(Optional.of(offer));
        when(offer.getEstado()).thenReturn(EstadoOferta.ACTIVA);
        when(profile.getId()).thenReturn(3L);
        when(profile.isVisible()).thenReturn(true);
        when(applicant.getPerfiles()).thenReturn(List.of(profile));
    }

    @Test
    void aplicaConPerfilVisibleCvYNombreEmpresaReservado() {
        Cv cv = mock(Cv.class);
        RubroEmpleo sector = mock(RubroEmpleo.class);
        when(cv.getId()).thenReturn(4L);
        when(cv.getNombreArchivo()).thenReturn("cv.pdf");
        when(cvs.findByPostulanteIdAndPerfilLaboralId(1L, 3L)).thenReturn(Optional.of(cv));
        when(offer.getRubro()).thenReturn(sector);
        when(offer.nombreEmpresaParaPostulante()).thenReturn("Empresa reservada");
        when(profile.getNombre()).thenReturn("Ventas");
        when(applications.saveAndFlush(any(Postulacion.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.apply("applicant@example.test", 2L, 3L);

        assertEquals(EstadoPostulacion.RECIBIDA, response.estado());
        assertEquals(3L, response.perfilId());
        assertEquals(4L, response.cvId());
        assertEquals("cv.pdf", response.cvNombre());
        assertEquals("Empresa reservada", response.socioNombre());
        verify(em).refresh(any(Postulacion.class));
    }

    @Test
    void rechazaOfertaCerradaVencidaODuplicadaSinGuardar() {
        when(offer.getEstado()).thenReturn(EstadoOferta.PAUSADA);
        assertStatus(HttpStatus.CONFLICT, () -> service.apply("applicant@example.test", 2L, 3L));
        when(offer.getEstado()).thenReturn(EstadoOferta.ACTIVA);
        when(offer.getFechaCierre()).thenReturn(LocalDate.now().minusDays(2));
        assertStatus(HttpStatus.CONFLICT, () -> service.apply("applicant@example.test", 2L, 3L));
        when(offer.getFechaCierre()).thenReturn(LocalDate.now().plusDays(2));
        when(applications.existsByPostulanteIdAndOfertaId(1L, 2L)).thenReturn(true);
        assertStatus(HttpStatus.CONFLICT, () -> service.apply("applicant@example.test", 2L, 3L));
        verify(applications, never()).saveAndFlush(any());
    }

    @Test
    void exigePerfilPropioVisibleYCvAsociado() {
        assertStatus(HttpStatus.BAD_REQUEST, () -> service.apply("applicant@example.test", 2L, 9L));
        when(profile.isVisible()).thenReturn(false);
        assertStatus(HttpStatus.BAD_REQUEST, () -> service.apply("applicant@example.test", 2L, 3L));
        when(profile.isVisible()).thenReturn(true);
        assertStatus(HttpStatus.BAD_REQUEST, () -> service.apply("applicant@example.test", 2L, 3L));
        verify(applications, never()).saveAndFlush(any());
    }

    @Test
    void listaHistoricoSinPerfilOCvYDelegaFiltroDeOfertas() {
        RubroEmpleo sector = mock(RubroEmpleo.class);
        when(offer.getRubro()).thenReturn(sector);
        when(applications.findByPostulanteIdOrderByFechaDesc(1L))
                .thenReturn(List.of(new Postulacion(applicant, offer)));
        var response = service.listMine("applicant@example.test").getFirst();
        assertNull(response.perfilId());
        assertNull(response.cvId());
        when(offerService.listForApplicants(5L)).thenReturn(List.of());
        assertTrue(service.listOffers(5L).isEmpty());
        verify(offerService).listForApplicants(5L);
    }

    @Test
    void rechazaPostulanteYOfertaInexistentes() {
        assertStatus(HttpStatus.FORBIDDEN, () -> service.listMine("missing@example.test"));
        assertStatus(HttpStatus.NOT_FOUND, () -> service.apply("applicant@example.test", 99L, 3L));
    }

    private void assertStatus(HttpStatus status, Runnable action) {
        assertEquals(status, assertThrows(ResponseStatusException.class, action::run).getStatusCode());
    }
}

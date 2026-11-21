package uy.ccisj.api.oferta;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.server.ResponseStatusException;
import uy.ccisj.api.postulante.Cv;
import uy.ccisj.api.socio.Socio;
import uy.ccisj.api.socio.SocioRepository;
import uy.ccisj.api.storage.S3StorageService;
import uy.ccisj.api.user.Role;
import uy.ccisj.api.user.User;
import uy.ccisj.api.user.UserRepository;

class SocioPostulacionControllerTest {
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final PostulacionRepository applications = mock(PostulacionRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final SocioRepository socios = mock(SocioRepository.class);
    private final S3StorageService storage = mock(S3StorageService.class);
    private SocioPostulacionController controller;

    @BeforeEach
    void setUp() {
        controller = new SocioPostulacionController(jdbc, applications, users, socios, storage, 15);
        User user = mock(User.class);
        when(user.getId()).thenReturn(1L);
        when(user.getRole()).thenReturn(Role.SOCIO);
        when(user.isActivo()).thenReturn(true);
        Socio socio = mock(Socio.class);
        when(socio.getId()).thenReturn(2L);
        when(users.findByEmailIgnoreCase("socio@example.test")).thenReturn(Optional.of(user));
        when(socios.findByUsuarioId(1L)).thenReturn(Optional.of(socio));
    }

    @Test
    void permiteTransicionesValidasEIdempotentes() {
        Postulacion application = mock(Postulacion.class);
        when(application.getId()).thenReturn(5L);
        when(application.getEstado()).thenReturn(EstadoPostulacion.RECIBIDA, EstadoPostulacion.REVISADA);
        when(applications.findByIdAndOfertaSocioId(5L, 2L)).thenReturn(Optional.of(application));

        var response = controller.updateStatus(auth(), 5L, EstadoPostulacion.REVISADA);

        assertEquals(5L, response.id());
        verify(application).setEstado(EstadoPostulacion.REVISADA);

        when(application.getEstado()).thenReturn(EstadoPostulacion.SELECCIONADA);
        controller.updateStatus(auth(), 5L, EstadoPostulacion.SELECCIONADA);
        verify(application).setEstado(EstadoPostulacion.SELECCIONADA);
    }

    @Test
    void rechazaTransicionesFinalesEInexistentes() {
        Postulacion selected = mock(Postulacion.class);
        when(selected.getEstado()).thenReturn(EstadoPostulacion.SELECCIONADA);
        when(applications.findByIdAndOfertaSocioId(5L, 2L)).thenReturn(Optional.of(selected));

        assertStatus(HttpStatus.CONFLICT,
                () -> controller.updateStatus(auth(), 5L, EstadoPostulacion.DESCARTADA));
        assertStatus(HttpStatus.NOT_FOUND,
                () -> controller.updateStatus(auth(), 9L, EstadoPostulacion.REVISADA));
    }

    @Test
    void generaEnlaceTemporalParaCvDisponible() {
        Postulacion application = mock(Postulacion.class);
        Cv cv = mock(Cv.class);
        when(cv.getRutaArchivoCv()).thenReturn("postulantes/1/cv.pdf");
        when(application.getCv()).thenReturn(cv);
        when(applications.findByIdAndOfertaSocioId(5L, 2L)).thenReturn(Optional.of(application));
        when(storage.presignedDownloadUrl(eq("postulantes/1/cv.pdf"), any(Duration.class)))
                .thenReturn("https://download.test/cv");

        assertEquals("https://download.test/cv", controller.downloadCv(auth(), 5L).url());
        verify(storage).presignedDownloadUrl("postulantes/1/cv.pdf", Duration.ofMinutes(15));
    }

    @Test
    void rechazaPostulacionSinCvOSinArchivo() {
        Postulacion application = mock(Postulacion.class);
        when(applications.findByIdAndOfertaSocioId(5L, 2L)).thenReturn(Optional.of(application));
        assertStatus(HttpStatus.NOT_FOUND, () -> controller.downloadCv(auth(), 5L));
        Cv cv = mock(Cv.class);
        when(application.getCv()).thenReturn(cv);
        when(cv.getRutaArchivoCv()).thenReturn(" ");
        assertStatus(HttpStatus.NOT_FOUND, () -> controller.downloadCv(auth(), 5L));
    }

    @Test
    void exigeCuentaSocioActivaYPerfilAsociado() {
        assertStatus(HttpStatus.FORBIDDEN,
                () -> controller.downloadCv(
                        new UsernamePasswordAuthenticationToken("missing@example.test", null), 5L));
        when(socios.findByUsuarioId(1L)).thenReturn(Optional.empty());
        assertStatus(HttpStatus.FORBIDDEN, () -> controller.downloadCv(auth(), 5L));
    }

    private UsernamePasswordAuthenticationToken auth() {
        return new UsernamePasswordAuthenticationToken("socio@example.test", null);
    }

    private void assertStatus(HttpStatus status, Runnable action) {
        assertEquals(status, assertThrows(ResponseStatusException.class, action::run).getStatusCode());
    }
}

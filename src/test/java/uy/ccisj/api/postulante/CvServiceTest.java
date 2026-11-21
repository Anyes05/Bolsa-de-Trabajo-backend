package uy.ccisj.api.postulante;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;
import uy.ccisj.api.storage.S3StorageService;
import uy.ccisj.api.user.Role;
import uy.ccisj.api.user.User;
import uy.ccisj.api.user.UserRepository;

class CvServiceTest {
    private final PostulanteRepository postulantes = mock(PostulanteRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final CvRepository cvs = mock(CvRepository.class);
    private final S3StorageService storage = mock(S3StorageService.class);
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private CvService service;
    private Postulante postulante;

    @BeforeEach
    void setUp() {
        service = new CvService(
                postulantes,
                users,
                cvs,
                storage,
                jdbc,
                2,
                15,
                "application/pdf, application/vnd.openxmlformats-officedocument.wordprocessingml.document");
        User user = new User("persona@example.com", null, "hash", Role.POSTULANTE);
        postulante = mock(Postulante.class);
        when(postulante.getId()).thenReturn(7L);
        when(users.findByEmailIgnoreCase("persona@example.com")).thenReturn(Optional.of(user));
        when(postulantes.findByUsuarioId(any())).thenReturn(Optional.of(postulante));
    }

    @Test
    void listaCvsConEnlacesTemporalesYPerfilOpcional() {
        Cv withProfile = cv(1L, "postulantes/7/cvs/uno.pdf", 11L);
        Cv withoutProfile = cv(2L, "postulantes/7/cvs/dos.pdf", null);
        when(cvs.findByPostulanteIdOrderByVersionDesc(7L)).thenReturn(List.of(withProfile, withoutProfile));
        when(storage.presignedDownloadUrl(anyString(), any(Duration.class)))
                .thenAnswer(invocation -> "https://download/" + invocation.getArgument(0));

        List<CvResponse> response = service.listMyCvs("persona@example.com");

        assertEquals(2, response.size());
        assertEquals(11L, response.getFirst().perfilLaboralId());
        assertEquals(null, response.get(1).perfilLaboralId());
        assertEquals("https://download/postulantes/7/cvs/uno.pdf", response.getFirst().downloadUrl());
    }

    @Test
    void descargaSoloCvPropioConDuracionConfigurada() {
        Cv cv = cv(1L, "postulantes/7/cvs/uno.pdf", 11L);
        when(cvs.findByIdAndPostulanteId(1L, 7L)).thenReturn(Optional.of(cv));
        when(storage.presignedDownloadUrl("postulantes/7/cvs/uno.pdf", Duration.ofMinutes(15)))
                .thenReturn("https://download/cv");

        assertEquals("https://download/cv", service.downloadLink("persona@example.com", 1L).url());

        when(cvs.findByIdAndPostulanteId(2L, 7L)).thenReturn(Optional.empty());
        assertStatus(HttpStatus.NOT_FOUND, () -> service.downloadLink("persona@example.com", 2L));
    }

    @Test
    void eliminaCvPropioYEncolaLaRutaActualBajoBloqueo() {
        Cv cv = cv(1L, "ruta-anterior.pdf", 11L);
        when(cvs.findByIdAndPostulanteId(1L, 7L)).thenReturn(Optional.of(cv));
        when(jdbc.queryForObject(anyString(), org.mockito.ArgumentMatchers.eq(Long.class), any(), any()))
                .thenReturn(11L);
        when(jdbc.queryForList(anyString(), org.mockito.ArgumentMatchers.eq(String.class), any()))
                .thenReturn(List.of("ruta-actual.pdf"));

        service.deleteCv("persona@example.com", 1L);

        verify(jdbc).update(
                "INSERT INTO cv_archivos_pendientes_borrado (ruta) VALUES (?) ON CONFLICT DO NOTHING",
                "ruta-actual.pdf");
        verify(cvs).delete(cv);
        verify(cvs).flush();
    }

    @Test
    void eliminarRechazaCvAjenoODesaparecidoTrasBloquear() {
        when(cvs.findByIdAndPostulanteId(1L, 7L)).thenReturn(Optional.empty());
        assertStatus(HttpStatus.NOT_FOUND, () -> service.deleteCv("persona@example.com", 1L));

        Cv cv = cv(2L, "ruta.pdf", 11L);
        when(cvs.findByIdAndPostulanteId(2L, 7L)).thenReturn(Optional.of(cv));
        when(jdbc.queryForObject(anyString(), org.mockito.ArgumentMatchers.eq(Long.class), any(), any()))
                .thenReturn(11L);
        when(jdbc.queryForList(anyString(), org.mockito.ArgumentMatchers.eq(String.class), any()))
                .thenReturn(List.of());
        assertStatus(HttpStatus.NOT_FOUND, () -> service.deleteCv("persona@example.com", 2L));
        verify(cvs, never()).delete(cv);
    }

    @Test
    void cargaRechazaArchivoAusenteVacioExcesivoYFirmaFalsa() {
        assertStatus(
                HttpStatus.BAD_REQUEST,
                () -> service.uploadCv("persona@example.com", null, null, 1L));
        assertStatus(
                HttpStatus.BAD_REQUEST,
                () -> service.uploadCv(
                        "persona@example.com",
                        new MockMultipartFile("file", "cv.pdf", "application/pdf", new byte[0]),
                        null,
                        1L));
        assertStatus(
                HttpStatus.BAD_REQUEST,
                () -> service.uploadCv(
                        "persona@example.com",
                        new MockMultipartFile("file", "cv.pdf", "application/pdf", new byte[2 * 1024 * 1024 + 1]),
                        null,
                        1L));
        assertStatus(
                HttpStatus.BAD_REQUEST,
                () -> service.uploadCv(
                        "persona@example.com",
                        new MockMultipartFile("file", "cv.pdf", "application/pdf", "no-es-pdf".getBytes()),
                        null,
                        1L));
    }

    @Test
    void exigeCuentaYPerfilDePostulante() {
        when(users.findByEmailIgnoreCase("missing@example.com")).thenReturn(Optional.empty());
        assertStatus(HttpStatus.FORBIDDEN, () -> service.listMyCvs("missing@example.com"));

        when(postulantes.findByUsuarioId(any())).thenReturn(Optional.empty());
        when(postulantes.findByUsuarioEmailIgnoreCase("persona@example.com")).thenReturn(Optional.empty());
        when(jdbc.query(anyString(), any(org.springframework.jdbc.core.ResultSetExtractor.class), any()))
                .thenReturn(null);
        assertStatus(HttpStatus.FORBIDDEN, () -> service.listMyCvs("persona@example.com"));
    }

    private static Cv cv(Long id, String route, Long profileId) {
        Cv cv = mock(Cv.class);
        when(cv.getId()).thenReturn(id);
        when(cv.getRutaArchivoCv()).thenReturn(route);
        when(cv.getResumen()).thenReturn("Resumen");
        when(cv.getNombreArchivo()).thenReturn("cv.pdf");
        when(cv.getMimeType()).thenReturn("application/pdf");
        when(cv.getSizeBytes()).thenReturn(100L);
        when(cv.getFechaCarga()).thenReturn(OffsetDateTime.parse("2026-11-21T12:00:00-03:00"));
        if (profileId != null) {
            PerfilLaboral profile = mock(PerfilLaboral.class);
            when(profile.getId()).thenReturn(profileId);
            when(cv.getPerfilLaboral()).thenReturn(profile);
        }
        return cv;
    }

    private static void assertStatus(HttpStatus expected, Runnable action) {
        ResponseStatusException error = assertThrows(ResponseStatusException.class, action::run);
        assertEquals(expected, error.getStatusCode());
    }
}

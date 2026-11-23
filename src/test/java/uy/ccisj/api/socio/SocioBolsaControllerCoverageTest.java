package uy.ccisj.api.socio;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.sql.ResultSet;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.security.core.Authentication;
import org.springframework.web.server.ResponseStatusException;
import uy.ccisj.api.mail.MailNotificationService;
import uy.ccisj.api.notificacion.NotificacionRepository;
import uy.ccisj.api.storage.S3StorageService;
import uy.ccisj.api.user.Role;
import uy.ccisj.api.user.User;
import uy.ccisj.api.user.UserRepository;

/**
 * Búsqueda de perfiles en la bolsa, descarga de CV y validación de la cuenta del socio.
 * Complementa a SocioBolsaNotificationTest (que cubre el envío de la notificación).
 */
class SocioBolsaControllerCoverageTest {
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final UserRepository users = mock(UserRepository.class);
    private final SocioRepository socios = mock(SocioRepository.class);
    private final S3StorageService storage = mock(S3StorageService.class);
    private final MailNotificationService mail = mock(MailNotificationService.class);
    private final NotificacionRepository notificaciones = mock(NotificacionRepository.class);
    private final Authentication auth = mock(Authentication.class);
    private final SocioBolsaController controller = new SocioBolsaController(
            jdbc, users, socios, storage, mail, notificaciones, 20);
    private final User socioUser = mock(User.class);

    @BeforeEach
    void setUp() {
        Socio socio = mock(Socio.class);
        when(auth.getName()).thenReturn("empresa@example.test");
        when(users.findByEmailIgnoreCase("empresa@example.test")).thenReturn(Optional.of(socioUser));
        when(socioUser.isActivo()).thenReturn(true);
        when(socioUser.getRole()).thenReturn(Role.SOCIO);
        when(socioUser.getId()).thenReturn(3L);
        when(socios.findByUsuarioId(3L)).thenReturn(Optional.of(socio));
        when(socio.getId()).thenReturn(7L);
    }

    private ResultSet filaPerfil(String rubros, boolean conPostulacion) throws Exception {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getLong("id")).thenReturn(12L);
        when(rs.getLong("postulante_id")).thenReturn(33L);
        when(rs.getString("nombre_completo")).thenReturn("Ana María Pérez");
        when(rs.getString("email")).thenReturn("ana@example.test");
        when(rs.getString("telefono")).thenReturn("099123456");
        when(rs.getString("perfil_nombre")).thenReturn("Ventas");
        when(rs.getString("zona_residencia")).thenReturn("San José");
        when(rs.getString("rubros")).thenReturn(rubros);
        when(rs.getString("disponibilidad_horaria")).thenReturn("Mañana");
        when(rs.getString("libreta")).thenReturn("A");
        when(rs.getBoolean("tiene_vehiculo")).thenReturn(true);
        when(rs.getBoolean("tiene_postulacion")).thenReturn(conPostulacion);
        when(rs.getBoolean("tiene_cv")).thenReturn(conPostulacion);
        when(rs.getString("cedula_identidad")).thenReturn(conPostulacion ? "12345678" : null);
        when(rs.getObject("edad")).thenReturn(conPostulacion ? 30 : null);
        when(rs.getString("descripcion_experiencia")).thenReturn(conPostulacion ? "Experiencia" : null);
        when(rs.getString("ultimo_empleo")).thenReturn(conPostulacion ? "Tienda" : null);
        when(rs.getString("nombre_archivo")).thenReturn(conPostulacion ? "cv.pdf" : null);
        when(rs.getLong("size_bytes")).thenReturn(conPostulacion ? 2048L : 0L);
        when(rs.wasNull()).thenReturn(!conPostulacion);
        when(rs.getBytes("foto_perfil")).thenReturn(null);
        when(rs.getString("google_photo_url")).thenReturn("https://example.test/foto.png");
        return rs;
    }

    @SuppressWarnings("unchecked")
    private void responderBusqueda(ResultSet rs, String rubroEsperado) {
        when(jdbc.query(anyString(), any(RowMapper.class), eq(7L), eq(rubroEsperado), eq(rubroEsperado)))
                .thenAnswer(invocation -> List.of(((RowMapper<Object>) invocation.getArgument(1)).mapRow(rs, 0)));
    }

    @Test
    void busquedaConRubroMapeaDatosAmpliadosCuandoHayPostulacion() throws Exception {
        responderBusqueda(filaPerfil("ventas|atención", true), "Ventas");

        List<SocioBolsaController.ApplicantProfileCard> perfiles = controller.search(auth, "  Ventas ");

        assertEquals(1, perfiles.size());
        var perfil = perfiles.getFirst();
        assertEquals(12L, perfil.id());
        assertEquals(33L, perfil.applicantId());
        assertEquals("Ana María Pérez", perfil.fullName());
        assertEquals("AM", perfil.initials());
        assertEquals("VENTAS", perfil.categoryLabel());
        assertEquals(List.of("ventas", "atención"), perfil.sectors());
        assertEquals("Mañana", perfil.availability());
        assertEquals("A", perfil.license());
        assertTrue(perfil.hasVehicle());
        assertTrue(perfil.hasApplication());
        assertTrue(perfil.hasCv());
        assertEquals("12345678", perfil.identityCard());
        assertEquals(30, perfil.age());
        assertEquals("Experiencia", perfil.experienceSummary());
        assertEquals("Tienda", perfil.latestJob());
        assertEquals("cv.pdf", perfil.cvFileName());
        assertEquals("PDF oficial · 2 KB", perfil.cvMeta());
        assertEquals("https://example.test/foto.png", perfil.fotoUrl());
        assertEquals("ana@example.test", perfil.email());
        assertEquals("099123456", perfil.phone());
        assertEquals("Ventas", perfil.profileName());
        assertEquals("San José", perfil.location());
    }

    @Test
    void busquedaSinRubroOEnBlancoNoFiltraYSinSectoresUsaOtros() throws Exception {
        responderBusqueda(filaPerfil("", false), null);

        var sinFiltro = controller.search(auth, null);
        var enBlanco = controller.search(auth, "   ");

        assertEquals(1, sinFiltro.size());
        var perfil = sinFiltro.getFirst();
        assertEquals("OTROS", perfil.categoryLabel());
        assertTrue(perfil.sectors().isEmpty());
        assertFalse(perfil.hasApplication());
        assertNull(perfil.identityCard());
        assertNull(perfil.age());
        assertEquals("PDF oficial", perfil.cvMeta());
        assertEquals(sinFiltro, enBlanco);
        verify(jdbc, times(2)).query(argThat(sql -> sql.contains("pl.visible = TRUE AND p.visible = TRUE")),
                any(RowMapper.class), eq(7L), isNull(), isNull());
    }

    @Test
    @SuppressWarnings("unchecked")
    void listaSoloLosCvsPostuladosALaEmpresa() throws Exception {
        OffsetDateTime fecha = OffsetDateTime.parse("2026-02-01T10:00:00-03:00");
        ResultSet rs = mock(ResultSet.class);
        when(rs.getLong("id")).thenReturn(5L);
        when(rs.getString("nombre_archivo")).thenReturn("cv.pdf");
        when(rs.getLong("size_bytes")).thenReturn(3L * 1024 * 1024);
        when(rs.wasNull()).thenReturn(false);
        when(rs.getBoolean("activo")).thenReturn(true);
        when(rs.getObject("fecha_carga", OffsetDateTime.class)).thenReturn(fecha);
        when(jdbc.query(anyString(), any(RowMapper.class), eq(12L), eq(7L)))
                .thenAnswer(invocation -> List.of(((RowMapper<Object>) invocation.getArgument(1)).mapRow(rs, 0)));

        var cvs = controller.listCvs(auth, 12L);

        assertEquals(List.of(new SocioBolsaController.ProfileCv(5L, "cv.pdf", "PDF oficial · 3.0 MB", true, fecha)), cvs);
    }

    @SuppressWarnings("unchecked")
    private void claveCv(String clave, boolean hayFila) throws Exception {
        ResultSet rs = mock(ResultSet.class);
        when(rs.next()).thenReturn(hayFila);
        when(rs.getString(1)).thenReturn(clave);
        when(jdbc.query(anyString(), any(ResultSetExtractor.class), eq(5L), eq(12L), eq(7L))).thenAnswer(invocation -> {
            return ((ResultSetExtractor<Object>) invocation.getArgument(1)).extractData(rs);
        });
    }

    @Test
    void descargaDeCvGeneraUrlFirmadaConLaDuracionConfigurada() throws Exception {
        claveCv("cvs/5.pdf", true);
        when(storage.presignedDownloadUrl("cvs/5.pdf", Duration.ofMinutes(20))).thenReturn("https://s3/firmada");

        assertEquals(new SocioBolsaController.CvLink("https://s3/firmada"), controller.downloadCv(auth, 12L, 5L));
    }

    @Test
    void descargaDeCvInexistenteOSinClaveDevuelve404() throws Exception {
        claveCv(null, false);
        assertEquals(HttpStatus.NOT_FOUND, assertThrows(ResponseStatusException.class,
                () -> controller.downloadCv(auth, 12L, 5L)).getStatusCode());

        claveCv("  ", true);
        assertEquals(HttpStatus.NOT_FOUND, assertThrows(ResponseStatusException.class,
                () -> controller.downloadCv(auth, 12L, 5L)).getStatusCode());
        verifyNoInteractions(storage);
    }

    @Test
    void cuentaInexistenteInactivaONoSocioNoAccedeALaBolsa() {
        when(users.findByEmailIgnoreCase("empresa@example.test")).thenReturn(Optional.empty());
        assertEquals(HttpStatus.FORBIDDEN, assertThrows(ResponseStatusException.class,
                () -> controller.search(auth, null)).getStatusCode());

        when(users.findByEmailIgnoreCase("empresa@example.test")).thenReturn(Optional.of(socioUser));
        when(socioUser.isActivo()).thenReturn(false);
        assertEquals(HttpStatus.FORBIDDEN, assertThrows(ResponseStatusException.class,
                () -> controller.listCvs(auth, 1L)).getStatusCode());

        when(socioUser.isActivo()).thenReturn(true);
        when(socioUser.getRole()).thenReturn(Role.ADMIN);
        assertEquals(HttpStatus.FORBIDDEN, assertThrows(ResponseStatusException.class,
                () -> controller.downloadCv(auth, 1L, 1L)).getStatusCode());
        verifyNoInteractions(jdbc, storage);
    }

    @Test
    void usuarioSocioSinFichaDeSocioNoAccede() {
        when(socios.findByUsuarioId(3L)).thenReturn(Optional.empty());

        var error = assertThrows(ResponseStatusException.class, () -> controller.contactApplications(auth, 1L));

        assertEquals(HttpStatus.FORBIDDEN, error.getStatusCode());
        assertEquals("Socio no encontrado", error.getReason());
    }

    @SuppressWarnings("unchecked")
    private void destinatario(String emailPostulante, String emailEmpresa) throws Exception {
        ResultSet rs = mock(ResultSet.class);
        when(rs.next()).thenReturn(true);
        when(rs.getLong("postulante_usuario_id")).thenReturn(99L);
        when(rs.getString("postulante_nombre")).thenReturn("Ana");
        when(rs.getString("postulante_email")).thenReturn(emailPostulante);
        when(rs.getString("razon_social")).thenReturn("Empresa");
        when(rs.getString("socio_email")).thenReturn(emailEmpresa);
        when(rs.getString("oferta_titulo")).thenReturn("Vendedor");
        when(jdbc.query(anyString(), any(ResultSetExtractor.class), eq(7L), eq(42L), eq(12L))).thenAnswer(invocation -> {
            return ((ResultSetExtractor<Object>) invocation.getArgument(1)).extractData(rs);
        });
    }

    @Test
    void notificacionSinEmailDelPostulanteODeLaEmpresaDevuelve404() throws Exception {
        String[][] casos = {{null, "empresa@example.test"}, {"  ", "empresa@example.test"},
                {"ana@example.test", null}, {"ana@example.test", " "}};
        for (String[] caso : casos) {
            destinatario(caso[0], caso[1]);
            assertEquals(HttpStatus.NOT_FOUND, assertThrows(ResponseStatusException.class,
                    () -> controller.notifyApplicant(auth, 12L,
                            new SocioBolsaController.ContactNotificationRequest(true, 42L))).getStatusCode());
        }
        verifyNoInteractions(mail, notificaciones);
    }
}

package uy.ccisj.api.postulante;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.List;
import java.util.Optional;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;
import uy.ccisj.api.mail.MailNotificationService;
import uy.ccisj.api.notificacion.NotificacionRepository;
import uy.ccisj.api.socio.Socio;
import uy.ccisj.api.socio.SocioBolsaController;
import uy.ccisj.api.socio.SocioRepository;
import uy.ccisj.api.storage.S3StorageService;
import uy.ccisj.api.user.Role;
import uy.ccisj.api.user.User;
import uy.ccisj.api.user.UserRepository;

@EnabledIfSystemProperty(named = "rf23TestJdbcUrl", matches = ".+")
class PostulanteDisponibilidadDatabaseTest {
    private static DriverManagerDataSource dataSource;
    private static JdbcTemplate jdbc;
    private static Long applicantId;
    private static Long userId;
    private static final String EMAIL = "rf23-applicant@example.test";
    private static final String OTHER = "rf23-other@example.test";
    private final UsernamePasswordAuthenticationToken authentication =
            new UsernamePasswordAuthenticationToken(EMAIL, null);
    private PostulanteDisponibilidadController controller;
    private SocioBolsaController bolsa;
    private AdminPostulanteController admin;

    @BeforeAll
    static void migrate() {
        String schema = "rf23_" + java.util.UUID.randomUUID().toString().replace("-", "");
        String url = System.getProperty("rf23TestJdbcUrl");
        dataSource = new DriverManagerDataSource(url + (url.contains("?") ? "&" : "?") + "currentSchema=" + schema + ",public",
                "rf23_test", "rf23_test");
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("CREATE EXTENSION IF NOT EXISTS pgcrypto WITH SCHEMA public");
        Flyway.configure().dataSource(dataSource).schemas(schema).target("32").load().migrate();
        for (String email : List.of(EMAIL, OTHER)) {
            Long id = jdbc.queryForObject("""
                    INSERT INTO usuarios (email, password_hash, rol) VALUES (?, 'test-only', 'POSTULANTE') RETURNING id
                    """, Long.class, email);
            Long postulante = jdbc.queryForObject("""
                    INSERT INTO postulantes (usuario_id, nombre_completo, cedula_identidad, telefono, zona_residencia)
                    VALUES (?, 'Cuenta RF23', ?, '091111111', 'Test') RETURNING id
                    """, Long.class, id, email);
            if (email.equals(EMAIL)) {
                userId = id;
                applicantId = postulante;
            }
            for (boolean visible : new boolean[] {true, false}) {
                jdbc.update("""
                        INSERT INTO perfiles_laborales (postulante_id, nombre, disponibilidad_horaria, visible)
                        VALUES (?, ?, 'FULL_TIME', ?)
                        """, postulante, visible ? "Ventas" : "Administracion", visible);
            }
        }
        Flyway.configure().dataSource(dataSource).schemas(schema).load().migrate();
        assertEquals(0L, jdbc.queryForObject("SELECT count(*) FROM postulantes WHERE busqueda_pausada", Long.class));
    }

    @BeforeEach
    void setUp() {
        jdbc.update("UPDATE postulantes SET busqueda_pausada = FALSE, visible = TRUE");
        jdbc.update("UPDATE usuarios SET activo = TRUE WHERE email = ?", EMAIL);
        controller = new PostulanteDisponibilidadController(jdbc);
        var users = mock(UserRepository.class);
        when(users.findByEmailIgnoreCase("admin@example.test"))
                .thenReturn(Optional.of(new User("admin@example.test", null, "test-only", Role.ADMIN)));
        var socioUser = mock(User.class);
        when(socioUser.isActivo()).thenReturn(true);
        when(socioUser.getRole()).thenReturn(Role.SOCIO);
        when(socioUser.getId()).thenReturn(123L);
        when(users.findByEmailIgnoreCase("socio@example.test")).thenReturn(Optional.of(socioUser));
        var socios = mock(SocioRepository.class);
        var socio = mock(Socio.class);
        when(socio.getId()).thenReturn(123L);
        when(socios.findByUsuarioId(123L)).thenReturn(Optional.of(socio));
        bolsa = new SocioBolsaController(jdbc, users, socios, mock(S3StorageService.class),
                mock(MailNotificationService.class), mock(NotificacionRepository.class), 15);
        admin = new AdminPostulanteController(jdbc, users, mock(S3StorageService.class), 15);
    }

    @Test
    void persistsPauseHidesAllProfilesOnlyFromSociosAndResumesOriginalVisibility() {
        assertFalse(controller.estado(authentication).busquedaPausada());
        assertEquals(1, socioProfiles(applicantId).size());
        controller.actualizar(authentication, new PostulanteDisponibilidadController.Solicitud(true));
        assertTrue(new PostulanteDisponibilidadController(new JdbcTemplate(dataSource)).estado(authentication).busquedaPausada());
        assertTrue(socioProfiles(applicantId).isEmpty());
        assertEquals(1, socioProfiles(otherApplicantId()).size());
        var adminProfiles = admin.list(new UsernamePasswordAuthenticationToken("admin@example.test", null))
                .stream().filter(p -> p.applicantId().equals(applicantId)).toList();
        assertEquals(2, adminProfiles.size());
        assertTrue(adminProfiles.stream().allMatch(p -> p.busquedaPausada() && !p.visibleEnBolsa() && p.estado().equals("INACTIVO")));
        assertTrue(adminProfiles.stream().allMatch(AdminPostulanteController.ApplicantProfile::cuentaActiva));
        assertEquals(1, adminProfiles.stream().filter(AdminPostulanteController.ApplicantProfile::perfilVisible).count());
        controller.actualizar(authentication, new PostulanteDisponibilidadController.Solicitud(false));
        assertEquals(List.of("Ventas"), socioProfiles(applicantId).stream().map(SocioBolsaController.ApplicantProfileCard::profileName).toList());
        assertEquals(1L, jdbc.queryForObject("SELECT count(*) FROM perfiles_laborales WHERE postulante_id = ? AND NOT visible",
                Long.class, applicantId));
        jdbc.update("UPDATE postulantes SET visible = FALSE WHERE id = ?", applicantId);
        controller.actualizar(authentication, new PostulanteDisponibilidadController.Solicitud(true));
        controller.actualizar(authentication, new PostulanteDisponibilidadController.Solicitud(false));
        assertTrue(socioProfiles(applicantId).isEmpty(), "Reanudar no debe deshacer la retencion");
    }

    @Test
    void validatesRequestAndNeverUpdatesAnotherApplicant() throws Exception {
        var mvc = MockMvcBuilders.standaloneSetup(controller).build();
        for (String body : List.of("{}", "{\"busquedaPausada\":null}", "{\"busquedaPausada\":{}}")) {
            mvc.perform(put("/postulante/disponibilidad-laboral").principal(authentication)
                    .contentType("application/json").content(body)).andExpect(status().isBadRequest());
        }
        mvc.perform(put("/postulante/disponibilidad-laboral").principal(authentication)
                .contentType("application/json").content("{\"busquedaPausada\":true,\"postulanteId\":" + otherApplicantId() + "}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.busquedaPausada").value(true));
        assertFalse(controller.estado(new UsernamePasswordAuthenticationToken(OTHER, null)).busquedaPausada());
        mvc.perform(get("/postulante/disponibilidad-laboral").principal(authentication))
                .andExpect(status().isOk()).andExpect(jsonPath("$.busquedaPausada").value(true));
        var missing = new UsernamePasswordAuthenticationToken("not-an-applicant@example.test", null);
        assertThrows(ResponseStatusException.class, () -> controller.estado(missing));
        assertThrows(ResponseStatusException.class, () -> controller.actualizar(missing, new PostulanteDisponibilidadController.Solicitud(true)));
        jdbc.update("UPDATE usuarios SET activo = FALSE WHERE id = ?", userId);
        assertThrows(ResponseStatusException.class, () -> controller.actualizar(authentication, new PostulanteDisponibilidadController.Solicitud(false)));
        assertTrue(jdbc.queryForObject("SELECT busqueda_pausada FROM postulantes WHERE id = ?", Boolean.class, applicantId));
    }

    @Test
    void loginPermanenceAndStaleJpaUpdatesCannotCancelPause() {
        var factory = new org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean();
        factory.setDataSource(dataSource);
        factory.setPackagesToScan("uy.ccisj.api");
        factory.setJpaVendorAdapter(new org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter());
        factory.afterPropertiesSet();
        try (var em = factory.getObject().createEntityManager()) {
            em.getTransaction().begin();
            Postulante loadedBeforePause = em.find(Postulante.class, applicantId);
            assertFalse(loadedBeforePause.isBusquedaPausada());
            controller.actualizar(authentication, new PostulanteDisponibilidadController.Solicitud(true));
            var repository = mock(PostulanteRepository.class);
            when(repository.findByUsuarioId(userId)).thenReturn(Optional.of(loadedBeforePause));
            when(repository.findByUsuarioEmailIgnoreCase(EMAIL)).thenReturn(Optional.of(loadedBeforePause));
            var retention = new RetencionPermanenciaService(repository, mock(NotificacionRepository.class),
                    mock(MailNotificationService.class), "http://example.test");
            loadedBeforePause.setVisible(false);
            retention.touchActividad(em.find(User.class, userId));
            retention.confirmarAutenticado(EMAIL);
            em.getTransaction().commit();
            em.clear();
            assertTrue(em.find(Postulante.class, applicantId).isBusquedaPausada());
            assertTrue(controller.estado(authentication).busquedaPausada());
            assertTrue(socioProfiles(applicantId).isEmpty());
        } finally {
            factory.destroy();
        }
    }

    private Long otherApplicantId() {
        return jdbc.queryForObject("SELECT p.id FROM postulantes p JOIN usuarios u ON u.id = p.usuario_id WHERE u.email = ?",
                Long.class, OTHER);
    }

    private List<SocioBolsaController.ApplicantProfileCard> socioProfiles(Long id) {
        return bolsa.search(new UsernamePasswordAuthenticationToken("socio@example.test", null), null)
                .stream().filter(p -> p.applicantId().equals(id)).toList();
    }
}

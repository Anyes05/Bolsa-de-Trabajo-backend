package uy.ccisj.api.auth;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Optional;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.server.ResponseStatusException;
import uy.ccisj.api.mail.MailNotificationService;
import uy.ccisj.api.postulante.AdminPostulanteController;
import uy.ccisj.api.postulante.PostulanteDatosPersonalesController;
import uy.ccisj.api.postulante.PostulanteRepository;
import uy.ccisj.api.socio.Socio;
import uy.ccisj.api.socio.SocioBolsaController;
import uy.ccisj.api.socio.SocioRepository;
import uy.ccisj.api.storage.S3StorageService;
import uy.ccisj.api.user.Role;
import uy.ccisj.api.user.User;
import uy.ccisj.api.user.UserRepository;

@EnabledIfSystemProperty(named = "testJdbcUrl", matches = ".+")
class ProfilePhotoDatabaseTest {
    private static JdbcTemplate jdbc;
    private static DriverManagerDataSource dataSource;
    private static ProfilePhotoService photos;
    private static Long applicantId;
    private static final String APPLICANT = "avatar-applicant@example.test";

    @BeforeAll
    static void migrateIsolatedDatabase() {
        String schema = "avatar_" + java.util.UUID.randomUUID().toString().replace("-", "");
        dataSource = uy.ccisj.api.support.DatabaseTestConfig.dataSource(schema);
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("CREATE EXTENSION IF NOT EXISTS pgcrypto WITH SCHEMA public");
        Flyway.configure().dataSource(dataSource).schemas(schema).target("27").load().migrate();
        for (String role : new String[] {"ADMIN", "SOCIO", "POSTULANTE"}) {
            jdbc.update("INSERT INTO usuarios (email, password_hash, rol, bps) VALUES (?, 'test-only', ?, ?)",
                    "avatar-" + role.toLowerCase() + "@example.test", role, role.equals("SOCIO") ? "987654321" : null);
        }
        jdbc.update("INSERT INTO usuarios (email, password_hash, rol) VALUES (?, 'test-only', 'POSTULANTE')", APPLICANT);
        applicantId = jdbc.queryForObject("""
                INSERT INTO postulantes (usuario_id, nombre_completo, cedula_identidad, telefono, zona_residencia, foto_perfil, foto_mime)
                SELECT id, 'Avatar Demo', 'avatar-test', '091234567', 'Test', decode('010203', 'hex'), 'image/png'
                FROM usuarios WHERE email = ? RETURNING id
                """, Long.class, APPLICANT);
        jdbc.update("""
                INSERT INTO perfiles_laborales (postulante_id, nombre, disponibilidad_horaria, libreta)
                VALUES (?, 'Ventas', 'FULL_TIME', 'A')
                """, applicantId);
        Flyway.configure().dataSource(dataSource).schemas(schema).load().migrate();
        photos = new ProfilePhotoService(jdbc);
    }

    @Test
    void registersGoogleApplicantAndPreservesPasswordRegistration() {
        var users = mock(UserRepository.class);
        when(users.existsByEmailIgnoreCase(anyString())).thenAnswer(invocation ->
                jdbc.queryForObject("SELECT COUNT(*) FROM usuarios WHERE lower(email) = lower(?)", Long.class,
                        invocation.getArgument(0, String.class)) > 0);
        when(users.findById(anyLong())).thenAnswer(invocation -> Optional.of(new User(
                jdbc.queryForObject("SELECT email FROM usuarios WHERE id = ?", String.class, invocation.getArgument(0, Long.class)),
                null, "test-only", Role.POSTULANTE)));
        var passwordEncoder = new BCryptPasswordEncoder();
        var jwt = mock(JwtService.class);
        var auth = spy(new AuthService(users, mock(PostulanteRepository.class), mock(SocioRepository.class),
                jdbc, passwordEncoder, jwt, mock(uy.ccisj.api.postulante.RetencionPermanenciaService.class)));
        doReturn(new AuthResponse("test-session", "google-demo@gmail.com", Role.POSTULANTE, "Google Demo", null, false))
                .when(auth).authResponse(any(User.class));
        String googlePhoto = "https://lh3.googleusercontent.com/demo";
        var identity = new GoogleTokenVerifier.GoogleIdentity("test-google-subject", "google-demo@gmail.com", "Google Demo", googlePhoto, true);
        when(jwt.googleRegistrationIdentity("registration-only")).thenReturn(identity);
        String sector = jdbc.queryForObject("SELECT nombre_rubro FROM rubros_empleo WHERE activo = true ORDER BY id LIMIT 1", String.class);
        var request = new GoogleRegistrationRequest("registration-only", "Edited Google Name", "google-card", "091111111",
                "Test", "Google Profile", java.util.List.of(sector), "FULL_TIME", false, "", "", true);
        var google = new GoogleAuthService(mock(GoogleTokenVerifier.class), users, auth, jwt, jdbc, passwordEncoder,
                mock(uy.ccisj.api.postulante.RetencionPermanenciaService.class));
        assertEquals("test-session", google.register(request).token());
        assertEquals(identity.subject(), jdbc.queryForObject("SELECT google_subject FROM usuarios WHERE email = ?",
                String.class, identity.email()));
        var personal = new PostulanteDatosPersonalesController(jdbc, photos).get(
                new UsernamePasswordAuthenticationToken(identity.email(), null));
        assertEquals(request.fullName(), personal.nombreCompleto());
        assertEquals(request.identityCard(), personal.cedulaIdentidad());
        assertEquals(googlePhoto, personal.fotoUrl());
        assertEquals(1L, jdbc.queryForObject("""
                SELECT COUNT(*) FROM usuarios u JOIN postulantes p ON p.usuario_id = u.id
                JOIN perfiles_laborales pl ON pl.postulante_id = p.id
                JOIN perfil_rubros pr ON pr.perfil_id = pl.id
                WHERE u.email = ? AND pl.nombre = ? AND pl.disponibilidad_horaria = 'FULL_TIME'
                """, Long.class, identity.email(), request.profileName()));
        assertFalse(passwordEncoder.matches("test-session", jdbc.queryForObject(
                "SELECT password_hash FROM usuarios WHERE email = ?", String.class, identity.email())));
        assertThrows(ResponseStatusException.class, () -> google.register(request));
        byte[] customPng = png(new byte[] {1});
        photos.upload(identity.email(), new MockMultipartFile("file", "custom.png", "image/png", customPng));
        assertEquals(dataUrl("image/png", customPng), photos.find(identity.email()));
        photos.remove(identity.email());
        assertNull(photos.find(identity.email()));
        assertNull(jdbc.queryForObject("SELECT google_photo_url FROM usuarios WHERE email = ?", String.class, identity.email()));

        var normal = new RegisterApplicantRequest("Password Demo", "password-card", "092222222", "password-demo@example.test",
                "Test-only-password-42", "Test", "Password Profile", java.util.List.of(sector), "PART_TIME", false, "", "", true);
        auth.registerApplicant(normal);
        assertNull(jdbc.queryForObject("SELECT google_subject FROM usuarios WHERE email = ?", String.class, normal.email()));
        assertTrue(passwordEncoder.matches(normal.password(), jdbc.queryForObject(
                "SELECT password_hash FROM usuarios WHERE email = ?", String.class, normal.email())));
    }

    @Test
    void preservesExistingApplicantPhotosAndReturnsThemInBothListings() {
        String expected = "data:image/png;base64,AQID";
        assertEquals(expected, photos.find(APPLICANT));
        var users = mock(UserRepository.class);
        when(users.findByEmailIgnoreCase("admin@example.test"))
                .thenReturn(Optional.of(new User("admin@example.test", null, "test-only", Role.ADMIN)));
        var admin = new AdminPostulanteController(jdbc, users, mock(S3StorageService.class), 15);
        assertEquals(expected, admin.list(new UsernamePasswordAuthenticationToken("admin@example.test", null))
                .stream().filter(profile -> profile.applicantId().equals(applicantId)).findFirst().orElseThrow().fotoUrl());
        var socioUser = mock(User.class);
        when(socioUser.isActivo()).thenReturn(true);
        when(socioUser.getRole()).thenReturn(Role.SOCIO);
        when(socioUser.getId()).thenReturn(123L);
        when(users.findByEmailIgnoreCase("socio@example.test")).thenReturn(Optional.of(socioUser));
        var socios = mock(SocioRepository.class);
        var socio = mock(Socio.class);
        when(socio.getId()).thenReturn(123L);
        when(socios.findByUsuarioId(123L)).thenReturn(Optional.of(socio));
        var bolsa = new SocioBolsaController(jdbc, users, socios, mock(S3StorageService.class),
                mock(MailNotificationService.class), mock(uy.ccisj.api.notificacion.NotificacionRepository.class), 15);
        var card = bolsa.search(new UsernamePasswordAuthenticationToken("socio@example.test", null), null)
                .stream().filter(profile -> profile.applicantId().equals(applicantId)).findFirst().orElseThrow();
        assertEquals(expected, card.fotoUrl());
        assertNull(card.identityCard());
        assertFalse(card.hasApplication());
        var personal = new PostulanteDatosPersonalesController(jdbc, photos);
        assertEquals(expected, personal.get(new UsernamePasswordAuthenticationToken(APPLICANT, null)).fotoUrl());
    }

    @Test
    void persistsReplacementsForEveryRoleAndRemovalWithoutAffectingOtherAccounts() {
        for (String role : new String[] {"admin", "socio", "postulante"}) {
            String email = "avatar-" + role + "@example.test";
            byte[] first = png(new byte[] {4, 5, 6});
            var file = new MockMultipartFile("file", "photo.png", "image/png", first);
            photos.upload(email, file);
            assertEquals(dataUrl("image/png", first), new ProfilePhotoService(jdbc).find(email));
            byte[] second = png(new byte[] {7});
            photos.upload(email, new MockMultipartFile("file", "photo.png", "image/png", second));
            assertEquals(dataUrl("image/png", second), new ProfilePhotoService(jdbc).find(email));
            photos.remove(email);
            assertNull(new ProfilePhotoService(jdbc).find(email));
            assertEquals("data:image/png;base64,AQID", photos.find(APPLICANT));
        }
    }

    @Test
    void reloadsSocioPhotoThroughJpaAndSessionWithoutOverwritingJdbcUploads() {
        String email = "photo-persistence-socio@example.test";
        Long id = jdbc.queryForObject("""
                INSERT INTO usuarios (email, password_hash, rol, bps)
                VALUES (?, 'test-only', 'SOCIO', 'photo-persistence') RETURNING id
                """, Long.class, email);
        photos.upload(email, new MockMultipartFile("file", "photo.png", "image/png", png(new byte[] {1, 2, 3})));
        var factory = new org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean();
        factory.setDataSource(dataSource);
        factory.setPackagesToScan("uy.ccisj.api");
        factory.setJpaVendorAdapter(new org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter());
        factory.afterPropertiesSet();
        try (var em = factory.getObject().createEntityManager()) {
            User loaded = em.find(User.class, id);
            assertArrayEquals(png(new byte[] {1, 2, 3}), loaded.getFotoPerfil());
            assertEquals("image/png", loaded.getFotoMime());
            byte[] webp = webp((byte) 4);
            photos.upload(email, new MockMultipartFile("file", "photo.webp", "image/webp", webp));
            em.getTransaction().begin();
            loaded.setPasswordHash("updated-test-only");
            em.getTransaction().commit();
            em.clear();
            User reloaded = em.find(User.class, id);
            assertArrayEquals(webp, reloaded.getFotoPerfil());
            assertEquals("image/webp", reloaded.getFotoMime());
            assertEquals(dataUrl("image/webp", webp),
                    new AuthController(mock(AuthService.class), new ProfilePhotoService(jdbc), mock(AuthRateLimitService.class)).currentUser(
                            new UsernamePasswordAuthenticationToken(email, null)).get("fotoUrl"));
            photos.remove(email);
            em.clear();
            assertNull(em.find(User.class, id).getFotoPerfil());
            assertNull(new ProfilePhotoService(jdbc).find(email));
        } finally {
            factory.destroy();
        }
    }

    private static byte[] png(byte[] extra) {
        byte[] header = FileSignatures.PNG_HEADER;
        byte[] out = java.util.Arrays.copyOf(header, header.length + extra.length);
        System.arraycopy(extra, 0, out, header.length, extra.length);
        return out;
    }

    private static byte[] webp(byte extra) {
        return new byte[] {'R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'E', 'B', 'P', extra};
    }

    private static String dataUrl(String mime, byte[] bytes) {
        return "data:" + mime + ";base64," + java.util.Base64.getEncoder().encodeToString(bytes);
    }
}

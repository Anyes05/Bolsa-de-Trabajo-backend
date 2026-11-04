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
import uy.ccisj.api.mail.MailNotificationService;
import uy.ccisj.api.postulante.AdminPostulanteController;
import uy.ccisj.api.postulante.PostulanteDatosPersonalesController;
import uy.ccisj.api.socio.Socio;
import uy.ccisj.api.socio.SocioBolsaController;
import uy.ccisj.api.socio.SocioRepository;
import uy.ccisj.api.storage.S3StorageService;
import uy.ccisj.api.user.Role;
import uy.ccisj.api.user.User;
import uy.ccisj.api.user.UserRepository;

@EnabledIfSystemProperty(named = "photoTestJdbcUrl", matches = ".+")
class ProfilePhotoDatabaseTest {
    private static JdbcTemplate jdbc;
    private static ProfilePhotoService photos;
    private static Long applicantId;
    private static final String APPLICANT = "avatar-applicant@example.test";

    @BeforeAll
    static void migrateIsolatedDatabase() {
        String schema = "avatar_" + java.util.UUID.randomUUID().toString().replace("-", "");
        String url = System.getProperty("photoTestJdbcUrl");
        var dataSource = new DriverManagerDataSource(url + (url.contains("?") ? "&" : "?") + "currentSchema=" + schema + ",public",
                "photo_test", "photo_test");
        Flyway.configure().dataSource(dataSource).schemas(schema).target("27").load().migrate();
        jdbc = new JdbcTemplate(dataSource);
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
                mock(MailNotificationService.class), 15);
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
            var file = new MockMultipartFile("file", "photo.png", "image/png", new byte[] {4, 5, 6});
            photos.upload(email, file);
            assertEquals("data:image/png;base64,BAUG", new ProfilePhotoService(jdbc).find(email));
            photos.upload(email, new MockMultipartFile("file", "photo.png", "image/png", new byte[] {7}));
            assertEquals("data:image/png;base64,Bw==", new ProfilePhotoService(jdbc).find(email));
            photos.remove(email);
            assertNull(new ProfilePhotoService(jdbc).find(email));
            assertEquals("data:image/png;base64,AQID", photos.find(APPLICANT));
        }
    }
}

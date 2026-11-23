package uy.ccisj.api.user;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Alta del administrador inicial y accesores de la entidad User.
 */
class UserBootstrapCoverageTest {
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final PasswordEncoder encoder = mock(PasswordEncoder.class);

    @Test
    void creaAdminNormalizandoEmailYHasheandoLaClave() {
        when(encoder.encode("Admin-Clave1")).thenReturn("hash-admin");

        new BootstrapUsers(jdbc, encoder, "  Admin@CCISJ.Example ", "Admin-Clave1").run();

        verify(jdbc).update(
                "INSERT INTO usuarios (email, password_hash, rol) VALUES (?, ?, 'ADMIN') ON CONFLICT (email) DO NOTHING",
                "admin@ccisj.example", "hash-admin");
    }

    @Test
    void sinEmailOClaveNoCreaAdmin() {
        new BootstrapUsers(jdbc, encoder, "", "Admin-Clave1").run();
        new BootstrapUsers(jdbc, encoder, "admin@example.test", "  ").run("--ignorado");
        verifyNoInteractions(jdbc);
        verify(encoder, org.mockito.Mockito.never()).encode(anyString());
    }

    @Test
    void entidadUserExponeYModificaSusDatos() {
        User user = new User("ana@example.test", "123", "hash", Role.SOCIO);
        assertTrue(user.isActivo());
        assertNull(user.getId());
        assertEquals("123", user.getBps());
        assertEquals(Role.SOCIO, user.getRole());

        OffsetDateTime baja = OffsetDateTime.parse("2026-01-31T10:00:00-03:00");
        user.setEmail("nuevo@example.test");
        user.setBps("456");
        user.setCedula("12345678");
        user.setPasswordHash("otro-hash");
        user.setActivo(false);
        user.setFechaBaja(baja);
        user.setGoogleSubject("sub-1");
        user.setGooglePhotoUrl("https://lh3.googleusercontent.com/a");
        user.setTokenVersion(2);

        assertEquals("nuevo@example.test", user.getEmail());
        assertEquals("456", user.getBps());
        assertEquals("12345678", user.getCedula());
        assertEquals("otro-hash", user.getPasswordHash());
        assertFalse(user.isActivo());
        assertEquals(baja, user.getFechaBaja());
        assertEquals("sub-1", user.getGoogleSubject());
        assertEquals("https://lh3.googleusercontent.com/a", user.getGooglePhotoUrl());
        assertEquals(3, user.bumpTokenVersion());
        assertEquals(3, user.getTokenVersion());
        assertNull(user.getPostulante());
        assertNull(user.getSocio());
    }

    @Test
    void fotoDePerfilSoloSeLeeDesdeLaBase() {
        User user = new User();
        assertNull(user.getFotoPerfil());
        assertNull(user.getFotoMime());
        assertEquals(0, user.getTokenVersion());
        assertFalse(user.isDebeCambiarPassword());

        ReflectionTestUtils.setField(user, "fotoPerfil", new byte[] {1, 2, 3});
        ReflectionTestUtils.setField(user, "fotoMime", "image/png");
        assertArrayEquals(new byte[] {1, 2, 3}, user.getFotoPerfil());
        assertEquals("image/png", user.getFotoMime());
    }

    @Test
    void rolesDisponibles() {
        assertArrayEquals(new Role[] {Role.ADMIN, Role.SOCIO, Role.POSTULANTE}, Role.values());
        assertEquals(Role.POSTULANTE, Role.valueOf("POSTULANTE"));
    }
}

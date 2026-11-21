package uy.ccisj.api.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;
import uy.ccisj.api.postulante.Postulante;
import uy.ccisj.api.postulante.PostulanteRepository;
import uy.ccisj.api.postulante.RetencionPermanenciaService;
import uy.ccisj.api.socio.Socio;
import uy.ccisj.api.socio.SocioRepository;
import uy.ccisj.api.user.Role;
import uy.ccisj.api.user.User;
import uy.ccisj.api.user.UserRepository;

class AuthServiceAccountTest {
    private final UserRepository users = mock(UserRepository.class);
    private final PostulanteRepository postulantes = mock(PostulanteRepository.class);
    private final SocioRepository socios = mock(SocioRepository.class);
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final PasswordEncoder passwords = mock(PasswordEncoder.class);
    private final JwtService jwt = mock(JwtService.class);
    private final RetencionPermanenciaService retention = mock(RetencionPermanenciaService.class);
    private AuthService auth;

    @BeforeEach
    void setUp() {
        auth = new AuthService(users, postulantes, socios, jdbc, passwords, jwt, retention);
        when(jwt.generate(any(User.class))).thenReturn("token");
    }

    @Test
    void loginNoSocioUsaEmailYCompletaNombreSegunRol() {
        User applicant = user("postulante@example.com", Role.POSTULANTE);
        Postulante profile = mock(Postulante.class);
        when(profile.getNombreCompleto()).thenReturn("Ana Postulante");
        when(users.findByEmailIgnoreCase("postulante@example.com")).thenReturn(Optional.of(applicant));
        when(passwords.matches("clave", "hash")).thenReturn(true);
        when(postulantes.findByUsuarioId(any())).thenReturn(Optional.of(profile));

        AuthResponse response = auth.login(
                new LoginRequest(Role.POSTULANTE, "postulante@example.com", "clave"));

        assertEquals("Ana Postulante", response.fullName());
        assertEquals("token", response.token());
        verify(retention).touchActividad(applicant);

        User admin = user("admin@example.com", Role.ADMIN);
        when(users.findByEmailIgnoreCase("admin@example.com")).thenReturn(Optional.of(admin));
        assertEquals(
                "Administrador",
                auth.login(new LoginRequest(Role.ADMIN, "admin@example.com", "clave")).fullName());
    }

    @Test
    void socioNormalizaIdentificadorYBuscaCedulaComoAlternativa() {
        User member = user("socio@example.com", Role.SOCIO);
        Socio socio = new Socio(member, "Empresa");
        when(users.findByBps("1234567")).thenReturn(Optional.empty());
        when(users.findByCedula("1234567")).thenReturn(Optional.of(member));
        when(passwords.matches("clave", "hash")).thenReturn(true);
        when(socios.findByUsuarioId(any())).thenReturn(Optional.of(socio));

        AuthResponse response = auth.login(new LoginRequest(Role.SOCIO, "1.234.567", "clave"));

        assertEquals("Empresa", response.fullName());
        assertFalse(response.esDirectivo());
        verify(users).findByCedula("1234567");
    }

    @Test
    void identificadorSocioSinDigitosYRolIncorrectoSeRechazan() {
        assertStatus(
                HttpStatus.UNAUTHORIZED,
                () -> auth.login(new LoginRequest(Role.SOCIO, "abc", "clave")));

        User applicant = user("persona@example.com", Role.POSTULANTE);
        when(users.findByEmailIgnoreCase("persona@example.com")).thenReturn(Optional.of(applicant));
        when(passwords.matches("clave", "hash")).thenReturn(true);
        assertStatus(
                HttpStatus.UNAUTHORIZED,
                () -> auth.login(new LoginRequest(Role.ADMIN, "persona@example.com", "clave")));
    }

    @Test
    void claveProvisionalVencidaBloqueaLoginYCambio() {
        User user = user("socio@example.com", Role.SOCIO);
        user.setDebeCambiarPassword(true);
        user.setPasswordProvisionalHasta(OffsetDateTime.now().minusMinutes(1));
        when(users.findByBps("123")).thenReturn(Optional.of(user));
        when(users.findByEmailIgnoreCase("socio@example.com")).thenReturn(Optional.of(user));
        when(passwords.matches("actual", "hash")).thenReturn(true);

        assertStatus(
                HttpStatus.FORBIDDEN,
                () -> auth.login(new LoginRequest(Role.SOCIO, "123", "actual")));
        assertStatus(
                HttpStatus.FORBIDDEN,
                () -> auth.changePassword(
                        "socio@example.com", new ChangePasswordRequest("actual", "Nueva1!x")));
        verify(users, never()).save(any());
    }

    @Test
    void cambioDeClaveValidaActualDiferenciaYRenuevaSesion() {
        User user = user("admin@example.com", Role.ADMIN);
        user.setDebeCambiarPassword(true);
        user.setPasswordProvisionalHasta(OffsetDateTime.now().plusHours(1));
        when(users.findByEmailIgnoreCase("admin@example.com")).thenReturn(Optional.of(user));
        when(passwords.matches("incorrecta", "hash")).thenReturn(false);
        when(passwords.matches("actual", "hash")).thenReturn(true);
        when(passwords.matches("Nueva1!x", "hash")).thenReturn(false);
        when(passwords.encode("Nueva1!x")).thenReturn("nuevo-hash");

        assertStatus(
                HttpStatus.BAD_REQUEST,
                () -> auth.changePassword(
                        "admin@example.com", new ChangePasswordRequest("incorrecta", "Nueva1!x")));

        when(passwords.matches("Misma1!x", "hash")).thenReturn(true);
        assertStatus(
                HttpStatus.BAD_REQUEST,
                () -> auth.changePassword(
                        "admin@example.com", new ChangePasswordRequest("actual", "Misma1!x")));

        AuthResponse response = auth.changePassword(
                "admin@example.com", new ChangePasswordRequest("actual", "Nueva1!x"));

        assertEquals("nuevo-hash", user.getPasswordHash());
        assertFalse(user.isDebeCambiarPassword());
        assertNull(user.getPasswordProvisionalHasta());
        assertEquals(1, user.getTokenVersion());
        assertEquals("token", response.token());
        verify(users).save(user);
    }

    @Test
    void logoutInvalidaTokensYHelpersContemplanUsuarioAusente() {
        User user = user("admin@example.com", Role.ADMIN);
        when(users.findByEmailIgnoreCase("admin@example.com")).thenReturn(Optional.of(user));
        when(users.findByEmailIgnoreCase("missing@example.com")).thenReturn(Optional.empty());

        auth.logout("admin@example.com");

        assertEquals(1, user.getTokenVersion());
        verify(users).save(user);
        assertNull(auth.applicantFullName("missing@example.com"));
        assertNull(auth.socioEsDirectivo("missing@example.com"));
        assertFalse(auth.debeCambiarPassword("missing@example.com"));
        assertStatus(HttpStatus.UNAUTHORIZED, () -> auth.logout("missing@example.com"));
    }

    private static User user(String email, Role role) {
        return new User(email, role == Role.SOCIO ? "123" : null, "hash", role);
    }

    private static void assertStatus(HttpStatus expected, Runnable action) {
        ResponseStatusException error = assertThrows(ResponseStatusException.class, action::run);
        assertEquals(expected, error.getStatusCode());
    }
}

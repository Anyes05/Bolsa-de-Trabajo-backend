package uy.ccisj.api.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;
import uy.ccisj.api.postulante.PostulanteRepository;
import uy.ccisj.api.postulante.RetencionPermanenciaService;
import uy.ccisj.api.socio.Socio;
import uy.ccisj.api.socio.SocioRepository;
import uy.ccisj.api.user.Role;
import uy.ccisj.api.user.User;
import uy.ccisj.api.user.UserRepository;

class AuthServiceLoginTest {
    private final UserRepository users = mock(UserRepository.class);
    private final PostulanteRepository postulantes = mock(PostulanteRepository.class);
    private final SocioRepository socios = mock(SocioRepository.class);
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final PasswordEncoder passwords = mock(PasswordEncoder.class);
    private final JwtService jwt = mock(JwtService.class);
    private final RetencionPermanenciaService retencion = mock(RetencionPermanenciaService.class);
    private AuthService auth;

    @BeforeEach
    void setUp() {
        auth = new AuthService(users, postulantes, socios, jdbc, passwords, jwt, retencion);
        when(passwords.matches("clave-ok", "hash-ok")).thenReturn(true);
        when(jwt.generate(any(User.class))).thenReturn("token");
    }

    @Test
    void socioActivoAunqueMorosoPuedeIngresar() {
        User user = new User(null, "1234567", "hash-ok", Role.SOCIO);
        user.setActivo(true);
        when(users.findByBps("1234567")).thenReturn(Optional.of(user));
        when(socios.findByUsuarioId(any())).thenReturn(Optional.of(new Socio(user, "Empresa Morosa SA")));

        AuthResponse response = auth.login(new LoginRequest(Role.SOCIO, "1234567", "clave-ok"));

        assertEquals("token", response.token());
        assertEquals(Role.SOCIO, response.role());
        verify(retencion).touchActividad(user);
    }

    @Test
    void socioDadoDeBajaNoPuedeIngresar() {
        User user = new User(null, "9999999", "hash-ok", Role.SOCIO);
        user.setActivo(false);
        when(users.findByBps("9999999")).thenReturn(Optional.of(user));

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> auth.login(new LoginRequest(Role.SOCIO, "9999999", "clave-ok")));

        assertEquals(HttpStatus.UNAUTHORIZED, error.getStatusCode());
        verify(retencion, never()).touchActividad(any());
        verify(jwt, never()).generate(any());
    }

    @Test
    void socioImportadoSinAccesoNoPuedeIngresar() {
        User user = new User(null, "1234567", "hash-ok", Role.SOCIO);
        Socio socio = new Socio(user, "Importado SA");
        socio.setAccesoHabilitado(false);
        when(users.findByBps("1234567")).thenReturn(Optional.of(user));
        when(socios.findByUsuarioId(any())).thenReturn(Optional.of(socio));

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> auth.login(new LoginRequest(Role.SOCIO, "1234567", "clave-ok")));

        assertEquals(HttpStatus.FORBIDDEN, error.getStatusCode());
        verify(jwt, never()).generate(any());
    }

    @Test
    void passwordIncorrectaRechazaAunqueElUsuarioEsteActivo() {
        User user = new User(null, "1234567", "hash-ok", Role.SOCIO);
        when(users.findByBps("1234567")).thenReturn(Optional.of(user));
        when(passwords.matches("clave-mal", "hash-ok")).thenReturn(false);

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> auth.login(new LoginRequest(Role.SOCIO, "1234567", "clave-mal")));

        assertEquals(HttpStatus.UNAUTHORIZED, error.getStatusCode());
    }
}

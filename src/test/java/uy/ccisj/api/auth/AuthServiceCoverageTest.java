package uy.ccisj.api.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCreator;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;
import uy.ccisj.api.postulante.PostulanteRepository;
import uy.ccisj.api.postulante.RetencionPermanenciaService;
import uy.ccisj.api.socio.SocioRepository;
import uy.ccisj.api.user.Role;
import uy.ccisj.api.user.User;
import uy.ccisj.api.user.UserRepository;

/**
 * Casos borde de AuthService que no cubren los tests de login y cuenta: cambio de clave provisional,
 * identificadores de socio vacios y fallas al registrar postulantes.
 */
class AuthServiceCoverageTest {
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
        when(passwords.encode(anyString())).thenReturn("hash-nuevo");
    }

    private static RegisterApplicantRequest registro(List<String> rubros) {
        return new RegisterApplicantRequest("Ana Perez", "12345678", "099123456", " Ana@Example.test ",
                "Clave-123", "Centro", "Ventas", rubros, "FULL_TIME", false, null, null, true);
    }

    private static GoogleRegistrationRequest registroGoogle() {
        return new GoogleRegistrationRequest("token", "Ana Perez", "12345678", "099123456", "Centro", "Ventas",
                List.of("Comercio"), "FULL_TIME", false, null, null, true);
    }

    private User usuarioConClaveProvisional(OffsetDateTime vence) {
        User user = new User("ana@example.test", null, "hash", Role.POSTULANTE);
        user.setDebeCambiarPassword(true);
        user.setPasswordProvisionalHasta(vence);
        when(users.findByEmailIgnoreCase("ana@example.test")).thenReturn(Optional.of(user));
        when(passwords.matches("Provisoria-1", "hash")).thenReturn(true);
        when(passwords.matches("Definitiva-1", "hash")).thenReturn(false);
        return user;
    }

    @Test
    void cambioDeClaveDeUsuarioInexistenteEsNoAutenticado() {
        when(users.findByEmailIgnoreCase("nadie@example.test")).thenReturn(Optional.empty());
        var error = assertThrows(ResponseStatusException.class,
                () -> auth.changePassword("nadie@example.test", new ChangePasswordRequest("a", "b")));
        assertEquals(HttpStatus.UNAUTHORIZED, error.getStatusCode());
    }

    @Test
    void claveProvisionalSinVencimientoPuedeCambiarse() {
        User user = usuarioConClaveProvisional(null);

        AuthResponse response = auth.changePassword("ana@example.test", new ChangePasswordRequest("Provisoria-1", "Definitiva-1"));

        assertFalse(response.debeCambiarPassword());
        assertFalse(user.isDebeCambiarPassword());
        assertNull(user.getPasswordProvisionalHasta());
        assertEquals("hash-nuevo", user.getPasswordHash());
        assertEquals(1, user.getTokenVersion());
        verify(users).save(user);
    }

    @Test
    void claveDefinitivaSeCambiaSinRevisarPlazoProvisional() {
        User user = usuarioConClaveProvisional(OffsetDateTime.now().minusDays(10));
        user.setDebeCambiarPassword(false);

        AuthResponse response = auth.changePassword("ana@example.test", new ChangePasswordRequest("Provisoria-1", "Definitiva-1"));

        assertEquals("token", response.token());
        assertEquals("hash-nuevo", user.getPasswordHash());
        assertNull(user.getPasswordProvisionalHasta());
    }

    @Test
    void claveProvisionalVigentePuedeCambiarse() {
        User user = usuarioConClaveProvisional(OffsetDateTime.now().plusHours(3));
        auth.changePassword("ana@example.test", new ChangePasswordRequest("Provisoria-1", "Definitiva-1"));
        assertFalse(user.isDebeCambiarPassword());
    }

    @Test
    void claveProvisionalVencidaNoPuedeCambiarse() {
        User user = usuarioConClaveProvisional(OffsetDateTime.now().minusMinutes(1));
        var error = assertThrows(ResponseStatusException.class,
                () -> auth.changePassword("ana@example.test", new ChangePasswordRequest("Provisoria-1", "Definitiva-1")));
        assertEquals(HttpStatus.FORBIDDEN, error.getStatusCode());
        assertEquals("hash", user.getPasswordHash());
        verify(users, never()).save(any());
    }

    @Test
    void socioConIdentificadorNuloONoNumericoNoConsultaUsuarios() {
        for (String identificador : new String[] {null, "abc-"}) {
            var error = assertThrows(ResponseStatusException.class,
                    () -> auth.login(new LoginRequest(Role.SOCIO, identificador, "clave")));
            assertEquals(HttpStatus.UNAUTHORIZED, error.getStatusCode());
        }
        verify(users, never()).findByBps(any());
        verify(users, never()).findByCedula(any());
    }

    @Test
    void registroRechazaCedulaYaUsadaAunqueElEmailEsteLibre() {
        when(users.existsByEmailIgnoreCase("Ana@Example.test")).thenReturn(false);
        when(jdbc.queryForObject(anyString(), eq(Boolean.class), eq("12345678"))).thenReturn(true);

        var error = assertThrows(ResponseStatusException.class, () -> auth.registerApplicant(registro(List.of("Comercio"))));

        assertEquals(HttpStatus.CONFLICT, error.getStatusCode());
        verify(jdbc, never()).update(any(PreparedStatementCreator.class), any(KeyHolder.class));
    }

    @Test
    void registroFallaSiLaBaseNoDevuelveIdentificador() {
        when(jdbc.queryForObject(anyString(), eq(Boolean.class), eq("12345678"))).thenReturn(false);

        var error = assertThrows(IllegalStateException.class, () -> auth.registerApplicant(registro(List.of("Comercio"))));

        assertEquals("No se genero el identificador", error.getMessage());
    }

    @Test
    void registroRechazaRubrosInexistentesOInactivos() {
        when(jdbc.queryForObject(anyString(), eq(Boolean.class), eq("12345678"))).thenReturn(false);
        when(jdbc.update(any(PreparedStatementCreator.class), any(KeyHolder.class))).thenAnswer(invocation -> {
            ((GeneratedKeyHolder) invocation.getArgument(1)).getKeyList().add(Map.of("id", 10L));
            return 1;
        });
        when(jdbc.queryForList(anyString(), eq(Long.class), any(Object[].class))).thenReturn(List.of(1L));

        var error = assertThrows(ResponseStatusException.class,
                () -> auth.registerApplicant(registro(List.of("Comercio", "Inexistente", "Comercio"))));

        assertEquals(HttpStatus.BAD_REQUEST, error.getStatusCode());
        assertEquals("Uno o mas rubros no son validos", error.getReason());
        verify(jdbc, never()).update(eq("INSERT INTO perfil_rubros (perfil_id, rubro_id) VALUES (?, ?)"), any(), any());
    }

    @Test
    void registroGoogleRechazaSubjectYaVinculado() {
        var identity = new GoogleTokenVerifier.GoogleIdentity("sub-1", "ana@gmail.com", "Ana", null, true);
        when(jdbc.queryForObject(anyString(), eq(Boolean.class), eq("12345678"))).thenReturn(false);
        when(users.findByGoogleSubject("sub-1")).thenReturn(Optional.of(new User("otra@gmail.com", null, "h", Role.POSTULANTE)));

        var error = assertThrows(ResponseStatusException.class, () -> auth.registerGoogleApplicant(registroGoogle(), identity));

        assertEquals(HttpStatus.CONFLICT, error.getStatusCode());
        verify(passwords, never()).encode(anyString());
    }
}

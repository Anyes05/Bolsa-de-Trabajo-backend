package uy.ccisj.api.auth;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.sql.ResultSet;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;
import uy.ccisj.api.postulante.RetencionPermanenciaService;
import uy.ccisj.api.user.Role;
import uy.ccisj.api.user.User;
import uy.ccisj.api.user.UserRepository;

/**
 * Servicios de apoyo del modulo auth: registro Google duplicado, ventana del rate limit,
 * foto de perfil sin usuario y politica de claves.
 */
class AuthSoporteCoverageTest {

    @Test
    void registroGoogleDeSubjectExistentePideIniciarSesion() {
        UserRepository users = mock(UserRepository.class);
        AuthService auth = mock(AuthService.class);
        JwtService jwt = mock(JwtService.class);
        GoogleAuthService google = new GoogleAuthService(mock(GoogleTokenVerifier.class), users, auth, jwt,
                mock(JdbcTemplate.class), mock(PasswordEncoder.class), mock(RetencionPermanenciaService.class));
        var request = new GoogleRegistrationRequest("registro", "Ana", "12345678", "099", "Centro", "Ventas",
                List.of("Comercio"), "FULL_TIME", false, null, null, true);
        when(jwt.googleRegistrationIdentity("registro"))
                .thenReturn(new GoogleTokenVerifier.GoogleIdentity("sub-1", "ana@gmail.com", "Ana", null, true));
        when(users.findByGoogleSubject("sub-1")).thenReturn(Optional.of(new User("ana@gmail.com", null, "h", Role.POSTULANTE)));

        var error = assertThrows(ResponseStatusException.class, () -> google.register(request));

        assertEquals(HttpStatus.CONFLICT, error.getStatusCode());
        verify(auth, never()).registerGoogleApplicant(any(), any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void rateLimitDescartaIntentosFueraDeLaVentana() {
        AuthRateLimitService rateLimit = new AuthRateLimitService();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.1");
        Map<String, List<Instant>> hits = (Map<String, List<Instant>>) ReflectionTestUtils.getField(rateLimit, "hits");
        List<Instant> viejos = new ArrayList<>();
        Instant haceUnaHora = Instant.now().minus(Duration.ofHours(1));
        for (int index = 0; index < 5; index++) {
            viejos.add(haceUnaHora);
        }
        hits.put("login|10.0.0.1|ana@example.test", viejos);

        // Cinco fallos de hace una hora ya no bloquean: se podan al evaluar la ventana de 15 minutos.
        assertDoesNotThrow(() -> rateLimit.assertLoginAllowed(request, " Ana@Example.test "));
        assertEquals(List.of(), hits.get("login|10.0.0.1|ana@example.test"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void fotoDeUsuarioInexistenteOInactivoEsNoEncontrada() throws Exception {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        ResultSet vacio = mock(ResultSet.class);
        when(vacio.next()).thenReturn(false);
        when(jdbc.query(anyString(), any(ResultSetExtractor.class), eq("nadie@example.test")))
                .thenAnswer(invocation -> ((ResultSetExtractor<String>) invocation.getArgument(1)).extractData(vacio));

        var error = assertThrows(ResponseStatusException.class, () -> new ProfilePhotoService(jdbc).find("nadie@example.test"));

        assertEquals(HttpStatus.NOT_FOUND, error.getStatusCode());
    }

    @Test
    void subirFotoNulaEsSolicitudInvalida() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        var error = assertThrows(ResponseStatusException.class, () -> new ProfilePhotoService(jdbc).upload("ana@example.test", null));
        assertEquals(HttpStatus.BAD_REQUEST, error.getStatusCode());
        verifyNoInteractions(jdbc);
    }

    @Test
    void claveNulaNoCumpleLaPolitica() {
        assertFalse(PasswordPolicy.isValid(null));
        assertFalse(PasswordPolicy.isValid("sinmayuscula1!"));
        assertTrue(PasswordPolicy.isValid("Valida-123"));
    }
}

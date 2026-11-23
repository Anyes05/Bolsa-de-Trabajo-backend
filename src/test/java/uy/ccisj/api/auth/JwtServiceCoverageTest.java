package uy.ccisj.api.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.Map;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import uy.ccisj.api.user.Role;
import uy.ccisj.api.user.User;

/**
 * Tokens de sesion: email, version de sesion y validacion estricta del rol y del proposito.
 */
class JwtServiceCoverageTest {
    private static final byte[] SECRETO = "clave-de-prueba-solo-para-tests-0123456789".getBytes();
    private final SecretKey key = Keys.hmacShaKeyFor(SECRETO);
    private final JwtService jwt = new JwtService(Base64.getEncoder().encodeToString(SECRETO), 30);

    private String firmar(Map<String, Object> claims) {
        return Jwts.builder().subject("ana@example.test").claims(claims)
                .expiration(Date.from(Instant.now().plusSeconds(300))).signWith(key).compact();
    }

    @Test
    void generaTokenConEmailRolYVersionDeSesion() {
        User user = new User("ana@example.test", null, "hash", Role.POSTULANTE);
        user.setTokenVersion(4);

        String token = jwt.generate(user);

        assertEquals("ana@example.test", jwt.extractEmail(token));
        assertEquals(4, jwt.sessionVersion(token));
        assertTrue(jwt.isValid(token));
        assertNotEquals(token, jwt.generate(user), "cada token lleva un jti distinto");
    }

    @Test
    void versionDeSesionAusenteONoNumericaEsCero() {
        assertEquals(0, jwt.sessionVersion(firmar(Map.of("role", "ADMIN"))));
        assertEquals(0, jwt.sessionVersion(firmar(Map.of("role", "ADMIN", "ver", "7"))));
        assertEquals(7, jwt.sessionVersion(firmar(Map.of("role", "ADMIN", "ver", 7L))));
    }

    @Test
    void isValidExigeRolConocidoYSinProposito() {
        assertTrue(jwt.isValid(firmar(Map.of("role", "SOCIO"))));
        assertFalse(jwt.isValid(firmar(Map.of("role", "SUPERUSUARIO"))), "rol desconocido");
        assertFalse(jwt.isValid(firmar(Map.of("role", 1))), "rol no textual");
        assertFalse(jwt.isValid(firmar(Map.of())), "sin rol");
        assertFalse(jwt.isValid(firmar(Map.of("role", "ADMIN", "purpose", "google-registration"))),
                "un token con proposito no es de sesion");
        assertFalse(jwt.isValid("basura"));
    }

    @Test
    void tokenDeRegistroGoogleConRolEsRechazado() {
        String token = firmar(Map.of("purpose", "google-registration", "role", "POSTULANTE"));
        var error = assertThrows(ResponseStatusException.class, () -> jwt.googleRegistrationIdentity(token));
        assertEquals(HttpStatus.UNAUTHORIZED, error.getStatusCode());
        assertEquals("Token de registro con Google invalido", error.getReason());
    }

    @Test
    void tokenDeRegistroGoogleIlegibleVencio() {
        var error = assertThrows(ResponseStatusException.class, () -> jwt.googleRegistrationIdentity("no-es-jwt"));
        assertEquals(HttpStatus.UNAUTHORIZED, error.getStatusCode());
        assertTrue(error.getReason().startsWith("El registro con Google vencio"));
    }
}

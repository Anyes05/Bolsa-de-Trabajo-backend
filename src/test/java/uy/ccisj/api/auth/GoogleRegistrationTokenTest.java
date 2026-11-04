package uy.ccisj.api.auth;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Base64;
import java.time.Instant;
import java.util.Date;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import uy.ccisj.api.user.Role;
import uy.ccisj.api.user.User;

class GoogleRegistrationTokenTest {
    private JwtService service() {
        byte[] key = new byte[32];
        new java.security.SecureRandom().nextBytes(key);
        return new JwtService(Base64.getEncoder().encodeToString(key), 480);
    }

    @Test
    void registrationTokenCannotAuthenticateAndAccessTokenCannotRegister() {
        var jwt = service();
        var identity = new GoogleTokenVerifier.GoogleIdentity("subject", "demo@gmail.com", "Demo", null, true);
        String registration = jwt.googleRegistrationToken(identity);
        assertFalse(jwt.isValid(registration));
        assertEquals(identity.email(), jwt.googleRegistrationIdentity(registration).email());
        assertEquals(identity.subject(), jwt.googleRegistrationIdentity(registration).subject());
        String access = jwt.generate(new User("demo@gmail.com", null, "unused", Role.POSTULANTE));
        assertTrue(jwt.isValid(access));
        assertThrows(ResponseStatusException.class, () -> jwt.googleRegistrationIdentity(access));
    }

    @Test
    void registrationLifetimeIsOneHourWithoutSessionRole() throws Exception {
        String token = service().googleRegistrationToken(
                new GoogleTokenVerifier.GoogleIdentity("subject", "demo@gmail.com", "Demo", null, true));
        var claims = new ObjectMapper().readTree(Base64.getUrlDecoder().decode(token.split("\\.")[1]));
        assertEquals(3600, claims.get("exp").asLong() - claims.get("iat").asLong());
        assertEquals("google-registration", claims.get("purpose").asText());
        assertFalse(claims.has("role"));
    }

    @Test
    void rejectsExpiredRegistration() {
        byte[] key = new byte[32];
        new java.security.SecureRandom().nextBytes(key);
        var jwt = new JwtService(Base64.getEncoder().encodeToString(key), 480);
        String expired = Jwts.builder().subject("subject").claim("purpose", "google-registration")
                .claim("googleSubject", "subject").claim("email", "demo@gmail.com").claim("fullName", "Demo")
                .expiration(Date.from(Instant.now().minusSeconds(60)))
                .signWith(Keys.hmacShaKeyFor(key)).compact();
        assertThrows(ResponseStatusException.class, () -> jwt.googleRegistrationIdentity(expired));
        assertFalse(jwt.isValid(expired));
    }

    @Test
    void rejectsTamperedAndForeignRegistrationTokens() {
        var jwt = service();
        String token = jwt.googleRegistrationToken(new GoogleTokenVerifier.GoogleIdentity("subject", "demo@gmail.com", "Demo", null, true));
        assertThrows(ResponseStatusException.class, () -> service().googleRegistrationIdentity(token));
        assertThrows(ResponseStatusException.class, () -> jwt.googleRegistrationIdentity("invalid"));
        int start = token.lastIndexOf('.') + 1;
        String tampered = token.substring(0, start) + (token.charAt(start) == 'A' ? 'B' : 'A') + token.substring(start + 1);
        assertThrows(ResponseStatusException.class, () -> jwt.googleRegistrationIdentity(tampered));
    }
}

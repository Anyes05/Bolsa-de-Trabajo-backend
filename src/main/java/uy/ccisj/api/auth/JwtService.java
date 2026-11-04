package uy.ccisj.api.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import uy.ccisj.api.user.User;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@Service
public class JwtService {
    private final SecretKey key;
    private final long expirationMinutes;

    public JwtService(@Value("${app.jwt.secret}") String secret, @Value("${app.jwt.expiration-minutes}") long expirationMinutes) {
        this.key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret));
        this.expirationMinutes = expirationMinutes;
    }

    public String generate(User user) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(user.getEmail())
                .claim("role", user.getRole().name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(expirationMinutes, ChronoUnit.MINUTES)))
                .signWith(key)
                .compact();
    }

    public String extractEmail(String token) {
        return claims(token).getSubject();
    }

    public String googleRegistrationToken(GoogleTokenVerifier.GoogleIdentity identity) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(identity.subject())
                .claim("purpose", "google-registration")
                .claim("email", identity.email())
                .claim("fullName", identity.fullName())
                .claim("fotoUrl", identity.fotoUrl())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(60, ChronoUnit.MINUTES)))
                .signWith(key).compact();
    }

    public GoogleTokenVerifier.GoogleIdentity googleRegistrationIdentity(String token) {
        Claims payload;
        try {
            payload = claims(token);
        } catch (JwtException | IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                    "El registro con Google vencio. Vuelve a autenticarte con Google.");
        }
        if (!"google-registration".equals(payload.get("purpose")) || payload.containsKey("role")) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Token de registro con Google invalido");
        }
        return new GoogleTokenVerifier.GoogleIdentity(payload.getSubject(), payload.get("email", String.class),
                payload.get("fullName", String.class), payload.get("fotoUrl", String.class), false);
    }

    public boolean isValid(String token) {
        try {
            Claims payload = claims(token);
            return payload.get("role") instanceof String role
                    && java.util.Set.of("ADMIN", "SOCIO", "POSTULANTE").contains(role)
                    && !payload.containsKey("purpose");
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private Claims claims(String token) {
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
    }
}
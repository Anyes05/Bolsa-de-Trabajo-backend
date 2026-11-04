package uy.ccisj.api.auth;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.googleapis.auth.oauth2.GooglePublicKeysManager;
import io.jsonwebtoken.Jwts;
import java.io.IOException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class GoogleTokenVerifierTest {
    private static KeyPair keys;
    private GooglePublicKeysManager manager;
    private GoogleTokenVerifier verifier;
    private static final String CLIENT = "test-client.apps.googleusercontent.com";

    @BeforeAll
    static void createKeys() throws Exception {
        var generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        keys = generator.generateKeyPair();
    }

    @BeforeEach
    void setup() throws Exception {
        manager = mock(GooglePublicKeysManager.class);
        when(manager.getPublicKeys()).thenReturn(List.of(keys.getPublic()));
        verifier = new GoogleTokenVerifier(new GoogleIdTokenVerifier.Builder(manager)
                .setAudience(List.of(CLIENT)).setAcceptableTimeSkewSeconds(0).build());
    }

    private String token(String audience, String issuer, Instant expiry, String email, boolean verified, String nonce) {
        return Jwts.builder().subject("google-subject").issuer(issuer)
                .audience().add(audience).and().expiration(Date.from(expiry))
                .issuedAt(Date.from(Instant.now().minusSeconds(10)))
                .claim("email", email).claim("email_verified", verified).claim("nonce", nonce)
                .claim("name", "Google Demo").claim("picture", "https://lh3.googleusercontent.com/test")
                .signWith(keys.getPrivate(), Jwts.SIG.RS256).compact();
    }

    @Test
    void acceptsSignedVerifiedIdentityAndNonce() {
        var identity = verifier.verify(token(CLIENT, "https://accounts.google.com", Instant.now().plusSeconds(300),
                "Demo@gmail.com", true, "nonce"), "nonce");
        assertEquals("google-subject", identity.subject());
        assertEquals("demo@gmail.com", identity.email());
        assertEquals("Google Demo", identity.fullName());
        assertEquals("https://lh3.googleusercontent.com/test", identity.fotoUrl());
        assertTrue(identity.authoritative());
    }

    @Test
    void rejectsWrongAudienceIssuerExpiryEmailAndNonce() {
        String[] invalid = {
                token("other-client", "https://accounts.google.com", Instant.now().plusSeconds(300), "demo@gmail.com", true, "nonce"),
                token(CLIENT, "https://example.test", Instant.now().plusSeconds(300), "demo@gmail.com", true, "nonce"),
                token(CLIENT, "https://accounts.google.com", Instant.now().minusSeconds(60), "demo@gmail.com", true, "nonce"),
                token(CLIENT, "https://accounts.google.com", Instant.now().plusSeconds(300), "demo@gmail.com", false, "nonce"),
                token(CLIENT, "https://accounts.google.com", Instant.now().plusSeconds(300), "demo@gmail.com", true, "wrong-nonce"),
        };
        for (String value : invalid) {
            assertEquals(HttpStatus.UNAUTHORIZED, assertThrows(ResponseStatusException.class,
                    () -> verifier.verify(value, "nonce")).getStatusCode());
        }
    }

    @Test
    void rejectsInvalidSignatureAndMalformedCredentials() throws Exception {
        String value = token(CLIENT, "https://accounts.google.com", Instant.now().plusSeconds(300), "demo@gmail.com", true, "nonce");
        var generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        when(manager.getPublicKeys()).thenReturn(List.of(generator.generateKeyPair().getPublic()));
        assertEquals(HttpStatus.UNAUTHORIZED, assertThrows(ResponseStatusException.class,
                () -> verifier.verify(value, "nonce")).getStatusCode());
        assertEquals(HttpStatus.UNAUTHORIZED, assertThrows(ResponseStatusException.class,
                () -> verifier.verify("not-a-token", "nonce")).getStatusCode());
    }

    @Test
    void requiresPasswordLinkingForNonGoogleHostedMail() {
        assertFalse(verifier.verify(token(CLIENT, "https://accounts.google.com", Instant.now().plusSeconds(300),
                "demo@example.test", true, "nonce"), "nonce").authoritative());
    }

    @Test
    void missingConfigurationDisablesOnlyGoogleAuthentication() {
        var unconfigured = new GoogleTokenVerifier("");
        var failure = assertThrows(ResponseStatusException.class,
                () -> unconfigured.verify("credential", "nonce"));
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, failure.getStatusCode());
    }

    @Test
    void reportsGoogleKeyServiceFailure() throws Exception {
        when(manager.getPublicKeys()).thenThrow(new IOException("service unavailable"));
        String value = token(CLIENT, "https://accounts.google.com", Instant.now().plusSeconds(300), "demo@gmail.com", true, "nonce");
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, assertThrows(ResponseStatusException.class,
                () -> verifier.verify(value, "nonce")).getStatusCode());
    }
}

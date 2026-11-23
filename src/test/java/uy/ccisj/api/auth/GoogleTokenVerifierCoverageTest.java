package uy.ccisj.api.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.googleapis.auth.oauth2.GooglePublicKeysManager;
import io.jsonwebtoken.Jwts;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.Signature;
import java.util.Base64;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * Completa los casos de GoogleTokenVerifier: claims faltantes o en blanco, foto no confiable,
 * dominio de Google Workspace y configuracion por client-id.
 */
class GoogleTokenVerifierCoverageTest {
    private static final String CLIENT = "test-client.apps.googleusercontent.com";
    private static KeyPair keys;
    private GooglePublicKeysManager manager;
    private GoogleTokenVerifier verifier;

    @BeforeAll
    static void crearClaves() throws Exception {
        var generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        keys = generator.generateKeyPair();
    }

    @BeforeEach
    void setUp() throws Exception {
        manager = mock(GooglePublicKeysManager.class);
        when(manager.getPublicKeys()).thenReturn(List.of(keys.getPublic()));
        verifier = new GoogleTokenVerifier(new GoogleIdTokenVerifier.Builder(manager)
                .setAudience(List.of(CLIENT)).setAcceptableTimeSkewSeconds(0).build());
    }

    private Map<String, Object> claimsBase() {
        Map<String, Object> claims = new HashMap<>();
        claims.put("sub", "google-subject");
        claims.put("email", "demo@example.test");
        claims.put("email_verified", true);
        claims.put("nonce", "nonce");
        return claims;
    }

    private String token(Map<String, Object> claims) {
        return Jwts.builder().claims(claims).issuer("https://accounts.google.com")
                .audience().add(CLIENT).and()
                .expiration(Date.from(Instant.now().plusSeconds(300)))
                .issuedAt(Date.from(Instant.now().minusSeconds(10)))
                .signWith(keys.getPrivate(), Jwts.SIG.RS256).compact();
    }

    private HttpStatus estadoDeRechazo(Map<String, Object> claims, String nonce) {
        String value = token(claims);
        return (HttpStatus) assertThrows(ResponseStatusException.class, () -> verifier.verify(value, nonce)).getStatusCode();
    }

    @Test
    void rechazaSubjectOEmailAusentesOEnBlanco() {
        for (String claim : new String[] {"sub", "email"}) {
            Map<String, Object> sinClaim = claimsBase();
            sinClaim.remove(claim);
            assertEquals(HttpStatus.UNAUTHORIZED, estadoDeRechazo(sinClaim, "nonce"), "sin " + claim);
            Map<String, Object> enBlanco = claimsBase();
            enBlanco.put(claim, "   ");
            assertEquals(HttpStatus.UNAUTHORIZED, estadoDeRechazo(enBlanco, "nonce"), claim + " en blanco");
        }
    }

    @Test
    void rechazaSubjectEnBlancoFirmadoManualmente() throws Exception {
        // jjwt descarta un "sub" en blanco al construir, asi que el token se arma y firma a mano.
        Base64.Encoder base64 = Base64.getUrlEncoder().withoutPadding();
        long ahora = Instant.now().getEpochSecond();
        String header = base64.encodeToString("{\"alg\":\"RS256\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));
        String payload = base64.encodeToString(("{\"iss\":\"https://accounts.google.com\",\"aud\":\"" + CLIENT
                + "\",\"sub\":\"   \",\"email\":\"demo@example.test\",\"email_verified\":true,\"nonce\":\"nonce\","
                + "\"iat\":" + (ahora - 10) + ",\"exp\":" + (ahora + 300) + "}").getBytes(StandardCharsets.UTF_8));
        Signature firma = Signature.getInstance("SHA256withRSA");
        firma.initSign(keys.getPrivate());
        firma.update((header + "." + payload).getBytes(StandardCharsets.US_ASCII));
        String token = header + "." + payload + "." + base64.encodeToString(firma.sign());

        var error = assertThrows(ResponseStatusException.class, () -> verifier.verify(token, "nonce"));
        assertEquals(HttpStatus.UNAUTHORIZED, error.getStatusCode());
    }

    @Test
    void rechazaNonceNuloOEnBlanco() {
        assertEquals(HttpStatus.UNAUTHORIZED, estadoDeRechazo(claimsBase(), null));
        assertEquals(HttpStatus.UNAUTHORIZED, estadoDeRechazo(claimsBase(), "  "));
    }

    @Test
    void sinNombreValidoUsaElEmailNormalizado() {
        Map<String, Object> claims = claimsBase();
        claims.put("email", "  Demo@Example.TEST ");
        claims.put("name", "   ");
        var identity = verifier.verify(token(claims), "nonce");
        assertEquals("demo@example.test", identity.email());
        assertEquals("demo@example.test", identity.fullName());
        assertNull(identity.fotoUrl());

        claims.put("name", 42);
        assertEquals("demo@example.test", verifier.verify(token(claims), "nonce").fullName());

        claims.put("name", "  Ana Perez ");
        assertEquals("Ana Perez", verifier.verify(token(claims), "nonce").fullName());
    }

    @Test
    void descartaFotosQueNoSonHttpsDeGoogle() {
        Map<String, Object> claims = claimsBase();
        for (Object picture : new Object[] {
                "http://lh3.googleusercontent.com/foto",
                "https://evil.example/foto",
                "https:/sin-host",
                12345}) {
            claims.put("picture", picture);
            assertNull(verifier.verify(token(claims), "nonce").fotoUrl(), String.valueOf(picture));
        }
        claims.put("picture", "https://lh3.googleusercontent.com/a/foto");
        assertEquals("https://lh3.googleusercontent.com/a/foto", verifier.verify(token(claims), "nonce").fotoUrl());
    }

    @Test
    void fotoConUriMalformadaInvalidaElToken() {
        Map<String, Object> claims = claimsBase();
        claims.put("picture", "https://lh3.googleusercontent.com/foto con espacios");
        assertEquals(HttpStatus.UNAUTHORIZED, estadoDeRechazo(claims, "nonce"));
    }

    @Test
    void dominioWorkspaceEsAutoritativoSoloSiNoEstaEnBlanco() {
        Map<String, Object> claims = claimsBase();
        claims.put("hd", "empresa.example");
        assertTrue(verifier.verify(token(claims), "nonce").authoritative());
        claims.put("hd", "  ");
        assertFalse(verifier.verify(token(claims), "nonce").authoritative());
        claims.remove("hd");
        assertFalse(verifier.verify(token(claims), "nonce").authoritative());
    }

    @Test
    void errorCriptograficoAlObtenerClavesEsTokenInvalido() throws Exception {
        when(manager.getPublicKeys()).thenThrow(new GeneralSecurityException("clave corrupta"));
        assertEquals(HttpStatus.UNAUTHORIZED, estadoDeRechazo(claimsBase(), "nonce"));
    }

    @Test
    void clientIdNuloDejaGoogleDeshabilitado() {
        var sinConfigurar = new GoogleTokenVerifier((String) null);
        var error = assertThrows(ResponseStatusException.class, () -> sinConfigurar.verify("credencial", "nonce"));
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, error.getStatusCode());
    }

    @Test
    void clientIdConfiguradoHabilitaLaVerificacion() {
        var configurado = new GoogleTokenVerifier("  " + CLIENT + "  ");
        // Con client-id ya no responde 503: una credencial ilegible se rechaza como invalida sin ir a la red.
        var error = assertThrows(ResponseStatusException.class, () -> configurado.verify("no-es-un-jwt", "nonce"));
        assertEquals(HttpStatus.UNAUTHORIZED, error.getStatusCode());
    }
}

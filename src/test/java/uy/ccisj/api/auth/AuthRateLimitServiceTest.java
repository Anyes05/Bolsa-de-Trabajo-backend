package uy.ccisj.api.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class AuthRateLimitServiceTest {

    @Test
    void clientIpPriorizaPrimerProxyYContemplaAusenciaDeDireccion() {
        HttpServletRequest proxied = request(" 203.0.113.8, 10.0.0.2 ", "127.0.0.1");
        HttpServletRequest direct = request(null, "198.51.100.4");
        HttpServletRequest unknown = request("   ", null);

        assertEquals("203.0.113.8", AuthRateLimitService.clientIp(proxied));
        assertEquals("198.51.100.4", AuthRateLimitService.clientIp(direct));
        assertEquals("unknown", AuthRateLimitService.clientIp(unknown));
    }

    @Test
    void bloqueaAlQuintoFalloDeLoginSinAfectarOtroUsuario() {
        AuthRateLimitService limiter = new AuthRateLimitService();
        HttpServletRequest request = request(null, "192.0.2.10");

        for (int attempt = 0; attempt < 5; attempt++) {
            limiter.assertLoginAllowed(request, " SOCIO@EXAMPLE.COM ");
            limiter.recordLoginFailure(request, "socio@example.com");
        }

        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                () -> limiter.assertLoginAllowed(request, "SOCIO@example.com"));

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, error.getStatusCode());
        assertDoesNotThrow(() -> limiter.assertLoginAllowed(request, "otro@example.com"));
    }

    @Test
    void loginExitosoLimpiaLosFallosAcumulados() {
        AuthRateLimitService limiter = new AuthRateLimitService();
        HttpServletRequest request = request(null, "192.0.2.11");

        for (int attempt = 0; attempt < 5; attempt++) {
            limiter.recordLoginFailure(request, null);
        }
        assertThrows(ResponseStatusException.class, () -> limiter.assertLoginAllowed(request, null));

        limiter.clearLogin(request, null);

        assertDoesNotThrow(() -> limiter.assertLoginAllowed(request, null));
    }

    @Test
    void solicitudPublicaSeRegistraYLaNumeroOnceSeBloquea() {
        AuthRateLimitService limiter = new AuthRateLimitService();
        HttpServletRequest request = request(null, "192.0.2.12");

        for (int attempt = 0; attempt < 10; attempt++) {
            limiter.assertPublicAllowed(request, "registro");
        }

        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                () -> limiter.assertPublicAllowed(request, "registro"));

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, error.getStatusCode());
        assertDoesNotThrow(() -> limiter.assertPublicAllowed(request, "recuperacion"));
    }

    private static HttpServletRequest request(String forwarded, String remoteAddress) {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("X-Forwarded-For")).thenReturn(forwarded);
        when(request.getRemoteAddr()).thenReturn(remoteAddress);
        return request;
    }
}

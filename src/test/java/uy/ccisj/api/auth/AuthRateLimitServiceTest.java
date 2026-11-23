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
    void clientIpUsaXRealIpSoloSiLlegaDesdeElProxyPropio() {
        // Desde el Nginx del host (loopback) o del contenedor (red privada) se confía en X-Real-IP.
        assertEquals("203.0.113.8", AuthRateLimitService.clientIp(request(" 203.0.113.8 ", "127.0.0.1")));
        assertEquals("203.0.113.9", AuthRateLimitService.clientIp(request("203.0.113.9", "172.18.0.3")));
        assertEquals("203.0.113.10", AuthRateLimitService.clientIp(request("203.0.113.10", "::1")));
        // Desde una IP pública el encabezado se ignora: lo podría escribir el atacante.
        assertEquals("198.51.100.4", AuthRateLimitService.clientIp(request("1.2.3.4", "198.51.100.4")));
        // Sin encabezado, o con un remoto que no es un literal de IP, se usa la conexión.
        assertEquals("127.0.0.1", AuthRateLimitService.clientIp(request("   ", "127.0.0.1")));
        assertEquals("127.0.0.1", AuthRateLimitService.clientIp(request(null, "127.0.0.1")));
        assertEquals("proxy.local", AuthRateLimitService.clientIp(request("1.2.3.4", "proxy.local")));
        assertEquals("999.1.1.1", AuthRateLimitService.clientIp(request("1.2.3.4", "999.1.1.1")));
        assertEquals("::zz", AuthRateLimitService.clientIp(request("1.2.3.4", "::zz")));
        assertEquals("unknown", AuthRateLimitService.clientIp(request("1.2.3.4", null)));
        assertEquals("unknown", AuthRateLimitService.clientIp(request("1.2.3.4", " ")));
    }

    @Test
    void xForwardedForFalsoNoEvitaElBloqueo() {
        AuthRateLimitService limiter = new AuthRateLimitService();
        for (int attempt = 0; attempt < 5; attempt++) {
            HttpServletRequest cadaVezOtraIp = request(null, "198.51.100.20");
            when(cadaVezOtraIp.getHeader("X-Forwarded-For")).thenReturn("10.9.9." + attempt);
            limiter.recordLoginFailure(cadaVezOtraIp, "ana@example.test");
        }
        HttpServletRequest otroIntento = request(null, "198.51.100.20");
        when(otroIntento.getHeader("X-Forwarded-For")).thenReturn("10.9.9.99");

        assertThrows(ResponseStatusException.class, () -> limiter.assertLoginAllowed(otroIntento, "ana@example.test"));
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

    private static HttpServletRequest request(String realIp, String remoteAddress) {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("X-Real-IP")).thenReturn(realIp);
        when(request.getRemoteAddr()).thenReturn(remoteAddress);
        return request;
    }
}

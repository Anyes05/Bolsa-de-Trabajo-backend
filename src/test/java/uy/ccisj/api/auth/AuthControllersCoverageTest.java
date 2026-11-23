package uy.ccisj.api.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.server.ResponseStatusException;
import uy.ccisj.api.postulante.RetencionPermanenciaService;
import uy.ccisj.api.user.Role;

/**
 * Controladores livianos del modulo auth: se instancian con servicios simulados y se verifica
 * que deleguen con los argumentos correctos y apliquen el rate limit donde corresponde.
 */
class AuthControllersCoverageTest {
    private final AuthService authService = mock(AuthService.class);
    private final ProfilePhotoService photos = mock(ProfilePhotoService.class);
    private final AuthRateLimitService rateLimit = mock(AuthRateLimitService.class);
    private final AuthController controller = new AuthController(authService, photos, rateLimit);
    private final MockHttpServletRequest http = new MockHttpServletRequest();
    private final Authentication postulante = new UsernamePasswordAuthenticationToken(
            "ana@example.test", null, List.of(new SimpleGrantedAuthority("ROLE_POSTULANTE")));

    @Test
    void loginExitosoLimpiaLosIntentosFallidos() {
        LoginRequest request = new LoginRequest(Role.POSTULANTE, "ana@example.test", "Clave-1");
        AuthResponse esperado = new AuthResponse("jwt", "ana@example.test", Role.POSTULANTE, "Ana", null, false);
        when(authService.login(request)).thenReturn(esperado);

        assertSame(esperado, controller.login(request, http));

        verify(rateLimit).assertLoginAllowed(http, "ana@example.test");
        verify(rateLimit).clearLogin(http, "ana@example.test");
        verify(rateLimit, never()).recordLoginFailure(any(), any());
    }

    @Test
    void loginConCredencialesInvalidasRegistraElFallo() {
        LoginRequest request = new LoginRequest(Role.SOCIO, "1234567", "mala");
        when(authService.login(request)).thenThrow(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Credenciales invalidas"));

        var error = assertThrows(ResponseStatusException.class, () -> controller.login(request, http));

        assertEquals(HttpStatus.UNAUTHORIZED, error.getStatusCode());
        verify(rateLimit).recordLoginFailure(http, "1234567");
        verify(rateLimit, never()).clearLogin(any(), any());
    }

    @Test
    void loginRechazadoPorOtroMotivoNoCuentaComoIntentoFallido() {
        LoginRequest request = new LoginRequest(Role.SOCIO, "1234567", "buena");
        when(authService.login(request)).thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "Sin acceso"));

        var error = assertThrows(ResponseStatusException.class, () -> controller.login(request, http));

        assertEquals(HttpStatus.FORBIDDEN, error.getStatusCode());
        verify(rateLimit, never()).recordLoginFailure(any(), any());
        verify(rateLimit, never()).clearLogin(any(), any());
    }

    @Test
    void logoutYCambioDeClaveUsanElUsuarioAutenticado() {
        controller.logout(postulante);
        verify(authService).logout("ana@example.test");

        ChangePasswordRequest request = new ChangePasswordRequest("Vieja-1", "Nueva-12");
        AuthResponse esperado = new AuthResponse("jwt2", "ana@example.test", Role.POSTULANTE, "Ana", null, false);
        when(authService.changePassword("ana@example.test", request)).thenReturn(esperado);
        assertSame(esperado, controller.changePassword(postulante, request));
    }

    @Test
    void registroAplicaRateLimitPublicoYDelega() {
        RegisterApplicantRequest request = new RegisterApplicantRequest("Ana Perez", "12345678", "099123456",
                "ana@example.test", "Clave-123", "Centro", "Ventas", List.of("Comercio"), "FULL_TIME",
                false, null, null, true);
        AuthResponse esperado = new AuthResponse("jwt", "ana@example.test", Role.POSTULANTE, "Ana Perez", null, false);
        when(authService.registerApplicant(request)).thenReturn(esperado);

        assertSame(esperado, controller.register(request, http));
        verify(rateLimit).assertPublicAllowed(http, "register");
    }

    @Test
    void disponibilidadNuncaRevelaSiElEmailOLaCedulaExisten() {
        var request = new AuthController.RegistrationAvailabilityRequest("ana@example.test", "12345678");
        assertEquals("ana@example.test", request.email());
        assertEquals("12345678", request.identityCard());

        var respuesta = controller.availability(request, http);

        assertFalse(respuesta.emailExists());
        assertFalse(respuesta.identityCardExists());
        verify(rateLimit).assertPublicAllowed(http, "availability");
        verifyNoInteractions(authService);
    }

    @Test
    void meDevuelveDatosDelUsuarioActual() {
        when(authService.applicantFullName("ana@example.test")).thenReturn("Ana Perez");
        when(authService.socioEsDirectivo("ana@example.test")).thenReturn(null);
        when(authService.debeCambiarPassword("ana@example.test")).thenReturn(true);
        when(photos.find("ana@example.test")).thenReturn("data:image/png;base64,AQID");

        Map<String, Object> respuesta = controller.currentUser(postulante);

        assertEquals(List.of("email", "roles", "fullName", "esDirectivo", "debeCambiarPassword", "fotoUrl"),
                List.copyOf(respuesta.keySet()));
        assertEquals("ana@example.test", respuesta.get("email"));
        assertEquals(postulante.getAuthorities(), respuesta.get("roles"));
        assertEquals("Ana Perez", respuesta.get("fullName"));
        assertEquals(null, respuesta.get("esDirectivo"));
        assertEquals(true, respuesta.get("debeCambiarPassword"));
        assertEquals("data:image/png;base64,AQID", respuesta.get("fotoUrl"));
    }

    @Test
    void googleAuthenticateLinkYRegisterDelegan() {
        GoogleAuthService google = mock(GoogleAuthService.class);
        GoogleAuthController googleController = new GoogleAuthController(google);
        var login = new GoogleAuthService.GoogleAuthResult("LOGIN", null, null, null, null, null);
        var link = new GoogleAuthService.GoogleAuthResult("LINKED", null, null, null, null, null);
        when(google.authenticate(eq("cred"), eq("nonce"), isNull())).thenReturn(login);
        when(google.authenticate("cred", "nonce", "Clave-1")).thenReturn(link);

        var credential = new GoogleAuthController.GoogleCredential("cred", "nonce");
        assertSame(login, googleController.authenticate(credential));
        var linkRequest = new GoogleAuthController.GoogleLinkRequest("cred", "nonce", "Clave-1");
        assertEquals("Clave-1", linkRequest.password());
        assertSame(link, googleController.link(linkRequest));

        GoogleRegistrationRequest registro = new GoogleRegistrationRequest("token", "Ana", "12345678", "099",
                "Centro", "Ventas", List.of("Comercio"), "PART_TIME", true, null, null, true);
        AuthResponse esperado = new AuthResponse("jwt", "ana@gmail.com", Role.POSTULANTE, "Ana", null, false);
        when(google.register(registro)).thenReturn(esperado);
        assertSame(esperado, googleController.register(registro));
    }

    @Test
    void fotoDePerfilSubeYBorraParaElUsuarioActual() {
        ProfilePhotoController fotoController = new ProfilePhotoController(photos);
        MockMultipartFile archivo = new MockMultipartFile("file", "a.png", "image/png", FileSignatures.PNG_HEADER);
        when(photos.upload("ana@example.test", archivo)).thenReturn("data:image/png;base64,xyz");

        ProfilePhotoController.PhotoResponse respuesta = fotoController.upload(postulante, archivo);

        assertEquals("data:image/png;base64,xyz", respuesta.fotoUrl());
        fotoController.remove(postulante);
        verify(photos).remove("ana@example.test");
    }

    @Test
    void permanenciaConfirmaPorTokenYToleraCuerpoVacio() {
        RetencionPermanenciaService retencion = mock(RetencionPermanenciaService.class);
        PermanenciaPublicController permanencia = new PermanenciaPublicController(retencion);

        permanencia.confirmar(new PermanenciaPublicController.TokenRequest("abc"));
        permanencia.confirmar(null);

        verify(retencion).confirmarPorToken("abc");
        verify(retencion).confirmarPorToken(null);
    }

    @Test
    void sectoresListaSoloRubrosActivosOrdenados() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForList("SELECT nombre_rubro FROM rubros_empleo WHERE activo = true ORDER BY nombre_rubro", String.class))
                .thenReturn(List.of("Comercio", "Industria"));

        assertEquals(List.of("Comercio", "Industria"), new SectorController(jdbc).list());
    }

    @Test
    void securityHeadersFilterAgregaCabecerasYContinuaLaCadena() throws Exception {
        SecurityHeadersFilter filter = new SecurityHeadersFilter();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/auth/me");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertEquals("nosniff", response.getHeader("X-Content-Type-Options"));
        assertEquals("DENY", response.getHeader("X-Frame-Options"));
        assertEquals("no-referrer", response.getHeader("Referrer-Policy"));
        assertEquals("none", response.getHeader("X-Permitted-Cross-Domain-Policies"));
        assertEquals("no-store", response.getHeader("Cache-Control"));
        assertSame(request, chain.getRequest());
        assertSame(response, chain.getResponse());
    }
}

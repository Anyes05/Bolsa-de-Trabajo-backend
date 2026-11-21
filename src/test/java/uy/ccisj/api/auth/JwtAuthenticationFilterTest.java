package uy.ccisj.api.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import uy.ccisj.api.user.Role;
import uy.ccisj.api.user.User;
import uy.ccisj.api.user.UserRepository;

class JwtAuthenticationFilterTest {
    private final JwtService jwt = mock(JwtService.class);
    private final UserRepository users = mock(UserRepository.class);
    private final JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwt, users);

    @BeforeEach
    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void autenticaTokenValidoConVersionVigente() throws Exception {
        User user = user(false, true, 3);
        when(jwt.isValid("token")).thenReturn(true);
        when(jwt.extractEmail("token")).thenReturn("socio@example.test");
        when(jwt.sessionVersion("token")).thenReturn(3);
        when(users.findByEmailIgnoreCase("socio@example.test")).thenReturn(Optional.of(user));

        run("Bearer token", "/socio/perfil");

        var authentication = SecurityContextHolder.getContext().getAuthentication();
        assertEquals("socio@example.test", authentication.getPrincipal());
        assertEquals("ROLE_SOCIO", authentication.getAuthorities().iterator().next().getAuthority());
    }

    @Test
    void ignoraCabeceraAusenteMalformadaOTokenInvalido() throws Exception {
        run(null, "/socio/perfil");
        run("Basic token", "/socio/perfil");
        when(jwt.isValid("bad")).thenReturn(false);
        run("Bearer bad", "/socio/perfil");
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(jwt).isValid("bad");
    }

    @Test
    void rechazaUsuarioAusenteInactivoOConSesionRevocada() throws Exception {
        prepare("missing", Optional.empty(), 0);
        run("Bearer missing", "/socio/perfil");
        prepare("inactive", Optional.of(user(false, false, 0)), 0);
        run("Bearer inactive", "/socio/perfil");
        prepare("revoked", Optional.of(user(false, true, 2)), 1);
        run("Bearer revoked", "/socio/perfil");
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void cambioDeClavePendienteSoloPermiteEndpointsDeCuenta() throws Exception {
        for (String allowed : new String[] {"/auth/change-password", "/auth/me", "/auth/logout"}) {
            SecurityContextHolder.clearContext();
            String token = allowed.substring(1);
            prepare(token, Optional.of(user(true, true, 1)), 1);
            run("Bearer " + token, allowed);
            assertEquals("socio@example.test",
                    SecurityContextHolder.getContext().getAuthentication().getPrincipal());
        }
        SecurityContextHolder.clearContext();
        prepare("blocked", Optional.of(user(true, true, 1)), 1);
        run("Bearer blocked", "/socio/perfil");
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    private void prepare(String token, Optional<User> user, int version) {
        when(jwt.isValid(token)).thenReturn(true);
        when(jwt.extractEmail(token)).thenReturn("socio@example.test");
        when(jwt.sessionVersion(token)).thenReturn(version);
        when(users.findByEmailIgnoreCase("socio@example.test")).thenReturn(user);
    }

    private User user(boolean passwordPending, boolean active, int version) {
        User user = mock(User.class);
        when(user.isActivo()).thenReturn(active);
        when(user.isDebeCambiarPassword()).thenReturn(passwordPending);
        when(user.getTokenVersion()).thenReturn(version);
        when(user.getEmail()).thenReturn("socio@example.test");
        when(user.getRole()).thenReturn(Role.SOCIO);
        return user;
    }

    private void run(String authorization, String path) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
        request.setServletPath(path);
        if (authorization != null) request.addHeader("Authorization", authorization);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
    }
}

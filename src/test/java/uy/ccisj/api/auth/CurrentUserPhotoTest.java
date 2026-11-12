package uy.ccisj.api.auth;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import uy.ccisj.api.user.Role;

class CurrentUserPhotoTest {
    @ParameterizedTest
    @EnumSource(Role.class)
    void retrievesStoredPhotoWhenOpeningANewSessionForEachRole(Role role) {
        var auth = mock(AuthService.class);
        var photos = mock(ProfilePhotoService.class);
        String email = role.name().toLowerCase() + "@example.test";
        when(auth.applicantFullName(email)).thenReturn("Cuenta de prueba");
        when(photos.find(email)).thenReturn("data:image/png;base64,AQID");
        var principal = new UsernamePasswordAuthenticationToken(email, null);

        assertEquals("data:image/png;base64,AQID",
                new AuthController(auth, photos, mock(AuthRateLimitService.class)).currentUser(principal).get("fotoUrl"));
        assertEquals("data:image/png;base64,AQID",
                new AuthController(auth, photos, mock(AuthRateLimitService.class)).currentUser(principal).get("fotoUrl"));
        verify(photos, times(2)).find(email);
    }

    @ParameterizedTest
    @EnumSource(Role.class)
    void returnsExplicitNullWhenPhotoWasRemovedOrNeverUploaded(Role role) {
        var photos = mock(ProfilePhotoService.class);
        var principal = new UsernamePasswordAuthenticationToken(role.name() + "@example.test", null);
        var response = new AuthController(mock(AuthService.class), photos, mock(AuthRateLimitService.class)).currentUser(principal);
        assertTrue(response.containsKey("fotoUrl"));
        assertNull(response.get("fotoUrl"));
    }
}

package uy.ccisj.api.auth;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;
import uy.ccisj.api.user.Role;
import uy.ccisj.api.user.User;
import uy.ccisj.api.user.UserRepository;

class GoogleAuthServiceTest {
    private final GoogleTokenVerifier verifier = mock(GoogleTokenVerifier.class);
    private final UserRepository users = mock(UserRepository.class);
    private final AuthService auth = mock(AuthService.class);
    private final JwtService jwt = mock(JwtService.class);
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final PasswordEncoder passwords = mock(PasswordEncoder.class);
    private final GoogleAuthService service = new GoogleAuthService(verifier, users, auth, jwt, jdbc, passwords);
    private final GoogleTokenVerifier.GoogleIdentity identity =
            new GoogleTokenVerifier.GoogleIdentity("subject", "demo@gmail.com", "Demo", null, true);

    @Test
    void startsNewRegistrationWithoutCreatingUserOrIssuingSession() {
        when(verifier.verify("credential", "nonce")).thenReturn(identity);
        when(jwt.googleRegistrationToken(identity)).thenReturn("registration-only");
        var result = service.authenticate("credential", "nonce", null);
        assertEquals("REGISTER", result.status());
        assertEquals("registration-only", result.registrationToken());
        assertEquals(identity.email(), result.email());
        assertNull(result.session());
        verifyNoInteractions(auth, jdbc);
        verify(users, never()).saveAndFlush(any());
    }

    @Test
    void linksExistingVerifiedGmailApplicantAndKeepsPassword() {
        var user = new User("demo@gmail.com", null, "existing-hash", Role.POSTULANTE);
        when(verifier.verify("credential", "nonce")).thenReturn(identity);
        when(users.findByEmailIgnoreCase(identity.email())).thenReturn(Optional.of(user));
        var result = service.authenticate("credential", "nonce", null);
        assertEquals("LOGIN", result.status());
        assertEquals("subject", user.getGoogleSubject());
        assertEquals("existing-hash", user.getPasswordHash());
        verify(users).saveAndFlush(user);
        verify(auth).authResponse(user);
    }

    @Test
    void usesStableGoogleSubjectEvenWhenGoogleEmailChanges() {
        var user = new User("original@gmail.com", null, "hash", Role.POSTULANTE);
        when(verifier.verify("credential", "nonce")).thenReturn(identity);
        when(users.findByGoogleSubject("subject")).thenReturn(Optional.of(user));
        assertEquals("LOGIN", service.authenticate("credential", "nonce", null).status());
        verify(users, never()).findByEmailIgnoreCase(anyString());
        verifyNoInteractions(jdbc);
    }

    @Test
    void rejectsOtherRolesInactiveAccountsAndDifferentGoogleIdentity() {
        when(verifier.verify("credential", "nonce")).thenReturn(identity);
        for (Role role : new Role[] {Role.ADMIN, Role.SOCIO}) {
            when(users.findByEmailIgnoreCase(identity.email())).thenReturn(Optional.of(new User(identity.email(), null, "hash", role)));
            assertEquals(HttpStatus.FORBIDDEN, assertThrows(ResponseStatusException.class,
                    () -> service.authenticate("credential", "nonce", null)).getStatusCode());
        }
        var user = new User(identity.email(), null, "hash", Role.POSTULANTE);
        user.setActivo(false);
        when(users.findByEmailIgnoreCase(identity.email())).thenReturn(Optional.of(user));
        assertEquals(HttpStatus.FORBIDDEN, assertThrows(ResponseStatusException.class,
                () -> service.authenticate("credential", "nonce", null)).getStatusCode());
        user.setActivo(true);
        user.setGoogleSubject("another-subject");
        assertEquals(HttpStatus.CONFLICT, assertThrows(ResponseStatusException.class,
                () -> service.authenticate("credential", "nonce", null)).getStatusCode());
        verifyNoInteractions(auth, jdbc);
    }

    @Test
    void requiresCorrectPasswordBeforeLinkingExternalEmail() {
        var external = new GoogleTokenVerifier.GoogleIdentity("subject", "demo@example.test", "Demo", null, false);
        var user = new User(external.email(), null, "hash", Role.POSTULANTE);
        when(verifier.verify("credential", "nonce")).thenReturn(external);
        when(users.findByEmailIgnoreCase(external.email())).thenReturn(Optional.of(user));
        assertEquals("LINK_REQUIRED", service.authenticate("credential", "nonce", null).status());
        assertNull(user.getGoogleSubject());
        assertEquals(HttpStatus.UNAUTHORIZED, assertThrows(ResponseStatusException.class,
                () -> service.authenticate("credential", "nonce", "wrong")).getStatusCode());
        when(passwords.matches("correct", "hash")).thenReturn(true);
        assertEquals("LOGIN", service.authenticate("credential", "nonce", "correct").status());
        assertEquals("subject", user.getGoogleSubject());
    }
}

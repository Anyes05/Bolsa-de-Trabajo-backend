package uy.ccisj.api.notificacion;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import uy.ccisj.api.user.Role;
import uy.ccisj.api.user.User;
import uy.ccisj.api.user.UserRepository;

class PostulanteNotificacionTest {
    private final NotificacionRepository notices = mock(NotificacionRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final User applicant = mock(User.class);
    private final NotificacionService service = new NotificacionService(notices, users);

    @BeforeEach
    void setUp() {
        when(users.findByEmailIgnoreCase("applicant@example.test")).thenReturn(Optional.of(applicant));
        when(applicant.getRole()).thenReturn(Role.POSTULANTE);
        when(applicant.isActivo()).thenReturn(true);
        when(applicant.getId()).thenReturn(99L);
    }

    @Test
    void listsOnlyTheAuthenticatedApplicantsNotifications() {
        var notice = new Notificacion(applicant, "Resultado", "Seleccionado/a", false);
        when(notices.findByUsuarioIdOrderByCreatedAtDesc(99L)).thenReturn(List.of(notice));
        var result = service.listForPostulante("applicant@example.test");
        assertEquals(1, result.size());
        assertEquals("Seleccionado/a", result.get(0).cuerpo());
        assertFalse(result.get(0).leida());
        verify(notices).findByUsuarioIdOrderByCreatedAtDesc(99L);
    }

    @Test
    void marksOwnNotificationAndAllOwnNotificationsRead() {
        var notice = new Notificacion(applicant, "Resultado", "No seleccionado/a", false);
        when(notices.findByIdAndUsuarioId(15L, 99L)).thenReturn(Optional.of(notice));
        when(notices.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        assertTrue(service.markOneReadPostulante("applicant@example.test", 15L).leida());
        service.markAllReadPostulante("applicant@example.test");
        verify(notices).markAllRead(99L);
    }

    @Test
    void cannotReadAnotherAccountsNotification() {
        when(notices.findByIdAndUsuarioId(15L, 99L)).thenReturn(Optional.empty());
        assertEquals(HttpStatus.NOT_FOUND, assertThrows(ResponseStatusException.class,
                () -> service.markOneReadPostulante("applicant@example.test", 15L)).getStatusCode());
        verify(notices, never()).save(any());
    }

    @Test
    void inactiveApplicantCannotReadNotifications() {
        when(applicant.isActivo()).thenReturn(false);
        assertEquals(HttpStatus.FORBIDDEN, assertThrows(ResponseStatusException.class,
                () -> service.listForPostulante("applicant@example.test")).getStatusCode());
        verifyNoInteractions(notices);
    }
}

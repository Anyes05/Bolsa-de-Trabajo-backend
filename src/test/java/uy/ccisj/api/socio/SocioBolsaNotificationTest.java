package uy.ccisj.api.socio;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.sql.ResultSet;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.security.core.Authentication;
import org.springframework.web.server.ResponseStatusException;
import uy.ccisj.api.mail.MailNotificationService;
import uy.ccisj.api.mail.MailTemplate;
import uy.ccisj.api.notificacion.Notificacion;
import uy.ccisj.api.notificacion.NotificacionRepository;
import uy.ccisj.api.storage.S3StorageService;
import uy.ccisj.api.user.Role;
import uy.ccisj.api.user.User;
import uy.ccisj.api.user.UserRepository;

class SocioBolsaNotificationTest {
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final UserRepository users = mock(UserRepository.class);
    private final SocioRepository socios = mock(SocioRepository.class);
    private final MailNotificationService mail = mock(MailNotificationService.class);
    private final NotificacionRepository notifications = mock(NotificacionRepository.class);
    private final Authentication auth = mock(Authentication.class);
    private final User applicant = new User("applicant@example.test", null, "hash", Role.POSTULANTE);
    private final SocioBolsaController controller = new SocioBolsaController(
            jdbc, users, socios, mock(S3StorageService.class), mail, notifications, 15);

    @BeforeEach
    void setUp() {
        User socioUser = mock(User.class);
        Socio socio = mock(Socio.class);
        when(auth.getName()).thenReturn("company@example.test");
        when(users.findByEmailIgnoreCase("company@example.test")).thenReturn(Optional.of(socioUser));
        when(socioUser.isActivo()).thenReturn(true);
        when(socioUser.getRole()).thenReturn(Role.SOCIO);
        when(socioUser.getId()).thenReturn(3L);
        when(socios.findByUsuarioId(3L)).thenReturn(Optional.of(socio));
        when(socio.getId()).thenReturn(7L);
        when(users.getReferenceById(99L)).thenReturn(applicant);
    }

    private void recipient(boolean exists) throws Exception {
        ResultSet rs = mock(ResultSet.class);
        when(rs.next()).thenReturn(exists);
        when(rs.getLong("postulante_usuario_id")).thenReturn(99L);
        when(rs.getString("postulante_nombre")).thenReturn("Persona de prueba");
        when(rs.getString("postulante_email")).thenReturn("applicant@example.test");
        when(rs.getString("razon_social")).thenReturn("Empresa de prueba");
        when(rs.getString("socio_email")).thenReturn("company@example.test");
        when(jdbc.query(anyString(), org.mockito.ArgumentMatchers.<ResultSetExtractor<Object>>any(),
                eq(7L), eq(12L))).thenAnswer(invocation -> {
                    ResultSetExtractor<Object> extractor = invocation.getArgument(1);
                    return extractor.extractData(rs);
                });
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void savesSameOutcomeAndContentForTheEmailRecipient(boolean selected) throws Exception {
        recipient(true);
        controller.notifyApplicant(auth, 12L, new SocioBolsaController.ContactNotificationRequest(selected));

        var notice = ArgumentCaptor.forClass(Notificacion.class);
        var content = ArgumentCaptor.forClass(MailTemplate.Content.class);
        var subject = ArgumentCaptor.forClass(String.class);
        var order = inOrder(notifications, mail);
        order.verify(notifications).saveAndFlush(notice.capture());
        order.verify(mail).send(eq("applicant@example.test"), subject.capture(), content.capture(),
                eq("company@example.test"));
        assertSame(applicant, notice.getValue().getUsuario());
        assertEquals(subject.getValue(), notice.getValue().getTitulo());
        assertEquals(content.getValue().plain(), notice.getValue().getCuerpo());
        assertTrue(notice.getValue().getCuerpo().contains(selected
                ? "fuiste seleccionado/a para continuar" : "no fuiste seleccionado/a para continuar"));
        assertTrue(notice.getValue().getCuerpo().contains("Empresa de prueba"));
        assertFalse(notice.getValue().isLeida());
        assertFalse(notice.getValue().isPopup());
    }

    @Test
    void missingRecipientDoesNotSendOrSave() throws Exception {
        recipient(false);
        assertEquals(HttpStatus.NOT_FOUND, assertThrows(ResponseStatusException.class,
                () -> controller.notifyApplicant(auth, 12L,
                        new SocioBolsaController.ContactNotificationRequest(true))).getStatusCode());
        verifyNoInteractions(mail, notifications);
    }

    @Test
    void nonSocioCannotNotify() {
        when(users.findByEmailIgnoreCase("company@example.test")).thenReturn(Optional.of(applicant));
        assertEquals(HttpStatus.FORBIDDEN, assertThrows(ResponseStatusException.class,
                () -> controller.notifyApplicant(auth, 12L,
                        new SocioBolsaController.ContactNotificationRequest(true))).getStatusCode());
        verifyNoInteractions(jdbc, mail, notifications);
    }

    @Test
    void databaseFailurePreventsEmail() throws Exception {
        recipient(true);
        doThrow(new IllegalStateException("database unavailable")).when(notifications).saveAndFlush(any());
        assertThrows(IllegalStateException.class, () -> controller.notifyApplicant(auth, 12L,
                new SocioBolsaController.ContactNotificationRequest(false)));
        verifyNoInteractions(mail);
    }

    @Test
    void smtpFailureIsPropagatedForTransactionRollback() throws Exception {
        recipient(true);
        doThrow(new ResponseStatusException(HttpStatus.BAD_GATEWAY, "SMTP unavailable"))
                .when(mail).send(anyString(), anyString(), any(MailTemplate.Content.class), anyString());
        assertEquals(HttpStatus.BAD_GATEWAY, assertThrows(ResponseStatusException.class,
                () -> controller.notifyApplicant(auth, 12L,
                        new SocioBolsaController.ContactNotificationRequest(false))).getStatusCode());
        assertNotNull(SocioBolsaController.class.getMethod("notifyApplicant",
                Authentication.class, Long.class, SocioBolsaController.ContactNotificationRequest.class)
                .getAnnotation(org.springframework.transaction.annotation.Transactional.class));
    }
}

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
import org.springframework.jdbc.core.RowMapper;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
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
        when(rs.getString("oferta_titulo")).thenReturn("Asistente administrativo");
        when(jdbc.query(anyString(), org.mockito.ArgumentMatchers.<ResultSetExtractor<Object>>any(),
                eq(7L), eq(42L), eq(12L))).thenAnswer(invocation -> {
                    ResultSetExtractor<Object> extractor = invocation.getArgument(1);
                    return extractor.extractData(rs);
                });
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void savesSameOutcomeAndContentForTheEmailRecipient(boolean selected) throws Exception {
        recipient(true);
        controller.notifyApplicant(auth, 12L, new SocioBolsaController.ContactNotificationRequest(selected, 42L));

        var notice = ArgumentCaptor.forClass(Notificacion.class);
        var content = ArgumentCaptor.forClass(MailTemplate.Content.class);
        var subject = ArgumentCaptor.forClass(String.class);
        var order = inOrder(notifications, mail);
        order.verify(notifications).saveAndFlush(notice.capture());
        order.verify(mail).send(eq("applicant@example.test"), subject.capture(), content.capture(),
                eq("company@example.test"));
        assertSame(applicant, notice.getValue().getUsuario());
        assertTrue(subject.getValue().contains("Asistente administrativo"));
        assertTrue(subject.getValue().contains(notice.getValue().getTitulo()));
        assertEquals(content.getValue().plain(), notice.getValue().getCuerpo());
        assertTrue(notice.getValue().getCuerpo().contains(selected
                ? "ha sido seleccionada para continuar" : "no ha sido seleccionada para continuar"));
        assertTrue(notice.getValue().getCuerpo().contains("Empresa de prueba"));
        assertTrue(notice.getValue().getCuerpo().contains("Oferta laboral: Asistente administrativo"));
        assertTrue(content.getValue().html().contains("Asistente administrativo"));
        assertTrue(content.getValue().html().contains("Empresa de prueba"));
        if (selected) assertTrue(content.getValue().plain().contains("no constituye una confirmación de contratación"));
        verify(jdbc).query(argThat(sql -> sql.contains("o.socio_id = s.id AND o.id = ?")
                        && sql.contains("a.perfil_laboral_id = pl.id AND a.postulante_id = p.id")),
                org.mockito.ArgumentMatchers.<ResultSetExtractor<Object>>any(), eq(7L), eq(42L), eq(12L));
        assertFalse(notice.getValue().isLeida());
        assertFalse(notice.getValue().isPopup());
    }

    @Test
    void missingRecipientDoesNotSendOrSave() throws Exception {
        recipient(false);
        assertEquals(HttpStatus.NOT_FOUND, assertThrows(ResponseStatusException.class,
                () -> controller.notifyApplicant(auth, 12L,
                        new SocioBolsaController.ContactNotificationRequest(true, 42L))).getStatusCode());
        verifyNoInteractions(mail, notifications);
    }

    @Test
    void nonSocioCannotNotify() {
        when(users.findByEmailIgnoreCase("company@example.test")).thenReturn(Optional.of(applicant));
        assertEquals(HttpStatus.FORBIDDEN, assertThrows(ResponseStatusException.class,
                () -> controller.notifyApplicant(auth, 12L,
                        new SocioBolsaController.ContactNotificationRequest(true, 42L))).getStatusCode());
        verifyNoInteractions(jdbc, mail, notifications);
    }

    @Test
    void databaseFailurePreventsEmail() throws Exception {
        recipient(true);
        doThrow(new IllegalStateException("database unavailable")).when(notifications).saveAndFlush(any());
        assertThrows(IllegalStateException.class, () -> controller.notifyApplicant(auth, 12L,
                new SocioBolsaController.ContactNotificationRequest(false, 42L)));
        verifyNoInteractions(mail);
    }

    @Test
    void smtpFailureIsPropagatedForTransactionRollback() throws Exception {
        recipient(true);
        doThrow(new ResponseStatusException(HttpStatus.BAD_GATEWAY, "SMTP unavailable"))
                .when(mail).send(anyString(), anyString(), any(MailTemplate.Content.class), anyString());
        assertEquals(HttpStatus.BAD_GATEWAY, assertThrows(ResponseStatusException.class,
                () -> controller.notifyApplicant(auth, 12L,
                        new SocioBolsaController.ContactNotificationRequest(false, 42L))).getStatusCode());
        assertNotNull(SocioBolsaController.class.getMethod("notifyApplicant",
                Authentication.class, Long.class, SocioBolsaController.ContactNotificationRequest.class)
                .getAnnotation(org.springframework.transaction.annotation.Transactional.class));
    }

    @Test
    void requiresAnExplicitOfferAndOutcome() throws Exception {
        MockMvc mvc = MockMvcBuilders.standaloneSetup(controller).build();
        for (String body : new String[] {
                "{\"selected\":true}", "{\"ofertaId\":42}", "{\"selected\":null,\"ofertaId\":42}",
                "{\"selected\":true,\"ofertaId\":0}", "{\"selected\":false,\"ofertaId\":-1}"}) {
            mvc.perform(post("/socio/bolsa/12/notificacion")
                    .contentType("application/json").content(body).principal(auth))
                    .andExpect(status().isBadRequest());
        }
        verifyNoInteractions(jdbc, mail, notifications);
    }

    @Test
    void listsRealApplicationsWithTheirOfferAndProfileScopedToTheCompany() throws Exception {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getLong("oferta_id")).thenReturn(42L);
        when(rs.getString("oferta_titulo")).thenReturn("Asistente administrativo");
        when(rs.getLong("perfil_id")).thenReturn(12L);
        when(rs.getString("perfil_nombre")).thenReturn("Administración");
        when(jdbc.query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<Object>>any(), eq(7L), eq(33L)))
                .thenAnswer(invocation -> {
                    RowMapper<Object> mapper = invocation.getArgument(1);
                    return java.util.List.of(mapper.mapRow(rs, 0));
                });
        var applications = controller.contactApplications(auth, 33L);
        assertEquals(1, applications.size());
        assertEquals(42L, applications.getFirst().ofertaId());
        assertEquals("Asistente administrativo", applications.getFirst().ofertaTitulo());
        assertEquals(12L, applications.getFirst().perfilId());
        verify(jdbc).query(argThat(sql -> sql.contains("o.socio_id = ?") && sql.contains("WHERE p.id = ?")
                        && sql.contains("p.id = pl.postulante_id")),
                org.mockito.ArgumentMatchers.<RowMapper<Object>>any(), eq(7L), eq(33L));
    }

    @Test
    void unrelatedOfferOrProfileDoesNotSendOrSave() throws Exception {
        recipient(true);
        assertEquals(HttpStatus.NOT_FOUND, assertThrows(ResponseStatusException.class,
                () -> controller.notifyApplicant(auth, 12L,
                        new SocioBolsaController.ContactNotificationRequest(true, 777L))).getStatusCode());
        assertEquals(HttpStatus.NOT_FOUND, assertThrows(ResponseStatusException.class,
                () -> controller.notifyApplicant(auth, 88L,
                        new SocioBolsaController.ContactNotificationRequest(false, 42L))).getStatusCode());
        verifyNoInteractions(mail, notifications);
    }
}

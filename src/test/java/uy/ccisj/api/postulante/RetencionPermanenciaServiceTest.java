package uy.ccisj.api.postulante;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import uy.ccisj.api.mail.MailNotificationService;
import uy.ccisj.api.mail.MailTemplate;
import uy.ccisj.api.notificacion.Notificacion;
import uy.ccisj.api.notificacion.NotificacionRepository;
import uy.ccisj.api.user.Role;
import uy.ccisj.api.user.User;

class RetencionPermanenciaServiceTest {
    private final PostulanteRepository postulantes = mock(PostulanteRepository.class);
    private final NotificacionRepository notificaciones = mock(NotificacionRepository.class);
    private final MailNotificationService mail = mock(MailNotificationService.class);
    private RetencionPermanenciaService service;

    @BeforeEach
    void setUp() {
        service = new RetencionPermanenciaService(postulantes, notificaciones, mail, " https://example.test/ ");
    }

    @Test
    void tocarActividadConfirmaPermanenciaYReactivaPerfil() {
        User user = user("persona@example.com", Role.POSTULANTE);
        Postulante postulante = postulante(user, "Ana");
        postulante.setVisible(false);
        postulante.setRetencionAvisadoAt(OffsetDateTime.now().minusDays(2));
        postulante.setRetencionDeadlineAt(OffsetDateTime.now().plusDays(5));
        postulante.setRetencionToken("token");
        OffsetDateTime anterior = postulante.getUltimaActividadAt();
        when(postulantes.findByUsuarioId(user.getId())).thenReturn(Optional.of(postulante));

        service.touchActividad(user);

        assertTrue(postulante.isVisible());
        assertFalse(postulante.getUltimaActividadAt().isBefore(anterior));
        assertNull(postulante.getRetencionAvisadoAt());
        assertNull(postulante.getRetencionDeadlineAt());
        assertNull(postulante.getRetencionToken());
        verify(postulantes).save(postulante);
    }

    @Test
    void tocarActividadIgnoraUsuariosNoAplicables() {
        service.touchActividad(null);
        service.touchActividad(user("admin@example.com", Role.ADMIN));

        verify(postulantes, never()).findByUsuarioId(any());
    }

    @Test
    void procesarAvisaEnCampanaYCorreoYOcultaVencidos() {
        Postulante aAvisar = postulante(user(" PERSONA@EXAMPLE.COM ", Role.POSTULANTE), " Ana ");
        Postulante aOcultar = postulante(user("otra@example.com", Role.POSTULANTE), "Otra");
        Postulante inactivo = postulante(inactiveUser(), "Inactivo");
        when(postulantes.findPendientesDeAviso(any())).thenReturn(List.of(aAvisar, inactivo));
        when(postulantes.findPendientesDeOcultar(any())).thenReturn(List.of(aOcultar));
        when(mail.isConfigured()).thenReturn(true);

        RetencionPermanenciaService.RetencionRunResult result = service.procesar();

        assertEquals(1, result.avisos());
        assertEquals(1, result.mailsOk());
        assertEquals(0, result.mailsFail());
        assertEquals(1, result.ocultados());
        assertNotNull(aAvisar.getRetencionToken());
        assertNotNull(aAvisar.getRetencionDeadlineAt());
        assertFalse(aOcultar.isVisible());
        assertNull(aOcultar.getRetencionToken());
        verify(notificaciones).save(any(Notificacion.class));
        verify(mail).send(eq("persona@example.com"), any(String.class), any(MailTemplate.Content.class));
    }

    @Test
    void procesarToleraSinEmailSinSmtpYFalloDeCorreo() {
        Postulante sinEmail = postulante(user(" ", Role.POSTULANTE), "Sin email");
        Postulante sinSmtp = postulante(user("smtp@example.com", Role.POSTULANTE), "Sin SMTP");
        when(postulantes.findPendientesDeAviso(any())).thenReturn(List.of(sinEmail, sinSmtp));
        when(postulantes.findPendientesDeOcultar(any())).thenReturn(List.of());
        when(mail.isConfigured()).thenReturn(false);

        RetencionPermanenciaService.RetencionRunResult noMail = service.procesar();

        assertEquals(2, noMail.avisos());
        assertEquals(0, noMail.mailsOk());
        verify(mail, never()).send(any(), any(), any(MailTemplate.Content.class));

        Postulante failure = postulante(user("fail@example.com", Role.POSTULANTE), "Falla");
        when(postulantes.findPendientesDeAviso(any())).thenReturn(List.of(failure));
        when(mail.isConfigured()).thenReturn(true);
        org.mockito.Mockito.doThrow(new RuntimeException("smtp")).when(mail)
                .send(any(), any(), any(MailTemplate.Content.class));

        RetencionPermanenciaService.RetencionRunResult failed = service.procesar();
        assertEquals(1, failed.mailsFail());
        assertEquals(0, failed.mailsOk());
    }

    @Test
    void estadoDistingueAvisoPendienteOcultoYSinAccion() {
        Postulante postulante = postulante(user("persona@example.com", Role.POSTULANTE), "Ana");
        when(postulantes.findByUsuarioEmailIgnoreCase("persona@example.com")).thenReturn(Optional.of(postulante));

        RetencionPermanenciaService.PermanenciaEstado initial = service.estado("persona@example.com");
        assertFalse(initial.requiereAccion());

        postulante.setRetencionAvisadoAt(OffsetDateTime.now().minusDays(1));
        postulante.setRetencionDeadlineAt(OffsetDateTime.now().plusDays(2));
        RetencionPermanenciaService.PermanenciaEstado pending = service.estado("persona@example.com");
        assertTrue(pending.requiereAccion());
        assertTrue(pending.avisoPendiente());

        postulante.setVisible(false);
        RetencionPermanenciaService.PermanenciaEstado hidden = service.estado("persona@example.com");
        assertTrue(hidden.ocultoPorRetencion());
        assertFalse(hidden.avisoPendiente());
    }

    @Test
    void confirmaPorSesionYTokenUnaSolaVez() {
        Postulante postulante = postulante(user("persona@example.com", Role.POSTULANTE), "Ana");
        postulante.setVisible(false);
        postulante.setRetencionAvisadoAt(OffsetDateTime.now());
        postulante.setRetencionToken("abc");
        when(postulantes.findByUsuarioEmailIgnoreCase("persona@example.com")).thenReturn(Optional.of(postulante));

        service.confirmarAutenticado("persona@example.com");
        assertTrue(postulante.isVisible());
        assertNull(postulante.getRetencionToken());

        postulante.setRetencionToken("xyz");
        when(postulantes.findByRetencionToken("xyz")).thenReturn(Optional.of(postulante));
        service.confirmarPorToken(" xyz ");
        assertNull(postulante.getRetencionToken());

        assertStatus(HttpStatus.BAD_REQUEST, () -> service.confirmarPorToken(" "));
        assertStatus(HttpStatus.NOT_FOUND, () -> service.confirmarPorToken("usado"));
    }

    @Test
    void fuerzaInactividadYRechazaCuentasInvalidas() {
        Postulante postulante = postulante(user("persona@example.com", Role.POSTULANTE), "Ana");
        postulante.setVisible(false);
        postulante.setRetencionToken("token");
        when(postulantes.findById(7L)).thenReturn(Optional.of(postulante));

        service.forzarInactividad(7L);

        assertTrue(postulante.isVisible());
        assertTrue(postulante.getUltimaActividadAt().isBefore(OffsetDateTime.now().minusMonths(2)));
        assertNull(postulante.getRetencionToken());
        assertStatus(HttpStatus.NOT_FOUND, () -> service.forzarInactividad(8L));
        assertStatus(HttpStatus.FORBIDDEN, () -> service.estado("missing@example.com"));
    }

    private User user(String email, Role role) {
        return new User(email, role == Role.SOCIO ? "123" : null, "hash", role);
    }

    private User inactiveUser() {
        User user = mock(User.class);
        when(user.isActivo()).thenReturn(false);
        return user;
    }

    private Postulante postulante(User user, String name) {
        return new Postulante(user, name, "12345678", "099123456", "San José");
    }

    private void assertStatus(HttpStatus expected, Runnable action) {
        ResponseStatusException error = assertThrows(ResponseStatusException.class, action::run);
        assertEquals(expected, error.getStatusCode());
    }
}

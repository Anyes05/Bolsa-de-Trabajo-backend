package uy.ccisj.api.postulante;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;
import uy.ccisj.api.mail.MailNotificationService;
import uy.ccisj.api.mail.MailTemplate;
import uy.ccisj.api.notificacion.Notificacion;
import uy.ccisj.api.notificacion.NotificacionRepository;
import uy.ccisj.api.user.Role;
import uy.ccisj.api.user.User;

/** Casos borde de RF-13 (retención por inactividad) que no cubre RetencionPermanenciaServiceTest. */
class RetencionPermanenciaServiceCoverageTest {
    private final PostulanteRepository postulantes = mock(PostulanteRepository.class);
    private final NotificacionRepository notificaciones = mock(NotificacionRepository.class);
    private final MailNotificationService mail = mock(MailNotificationService.class);

    @Test
    void urlWebNulaOVaciaUsaLaUrlPorDefectoEnElEnlace() {
        for (String webUrl : new String[] {null, "   "}) {
            RetencionPermanenciaService service = service(webUrl);
            Postulante postulante = postulante(usuario("ana@example.test", Role.POSTULANTE), "Ana");
            when(postulantes.findPendientesDeAviso(any())).thenReturn(List.of(postulante));
            when(postulantes.findPendientesDeOcultar(any())).thenReturn(List.of());

            service.procesar();

            assertThat(cuerpoUltimaNotificacion())
                    .contains("https://ccisj.cloud-ip.cc/permanencia?token=" + postulante.getRetencionToken());
        }
    }

    @Test
    void tocarActividadIgnoraUsuarioInactivoYNoLimpiaSiNoHabiaAviso() {
        RetencionPermanenciaService service = service("https://bolsa.test");
        User inactivo = usuario("baja@example.test", Role.POSTULANTE);
        inactivo.setActivo(false);
        service.touchActividad(inactivo);
        verify(postulantes, never()).findByUsuarioId(any());

        User activo = usuario("ana@example.test", Role.POSTULANTE);
        Postulante visibleSinAviso = postulante(activo, "Ana");
        visibleSinAviso.setRetencionToken("token-previo");
        OffsetDateTime antes = OffsetDateTime.now().minusDays(10);
        visibleSinAviso.setUltimaActividadAt(antes);
        when(postulantes.findByUsuarioId(activo.getId())).thenReturn(Optional.of(visibleSinAviso));

        service.touchActividad(activo);

        assertThat(visibleSinAviso.getUltimaActividadAt()).isAfter(antes);
        // Sin aviso previo y visible: no se toca el estado de retención.
        assertThat(visibleSinAviso.getRetencionToken()).isEqualTo("token-previo");
        verify(postulantes).save(visibleSinAviso);
    }

    @Test
    void tocarActividadReactivaPerfilOcultoAunqueNoTengaAviso() {
        RetencionPermanenciaService service = service("https://bolsa.test");
        User activo = usuario("ana@example.test", Role.POSTULANTE);
        Postulante oculto = postulante(activo, "Ana");
        oculto.setVisible(false);
        oculto.setRetencionToken("token");
        when(postulantes.findByUsuarioId(activo.getId())).thenReturn(Optional.of(oculto));

        service.touchActividad(activo);

        assertThat(oculto.isVisible()).isTrue();
        assertThat(oculto.getRetencionToken()).isNull();
    }

    @Test
    void procesarOmitePostulanteSinUsuarioYUsaSaludoGenericoSinNombreNiEmail() {
        RetencionPermanenciaService service = service("https://bolsa.test/");
        Postulante sinUsuario = postulante(null, "Huérfano");
        User sinEmail = usuario(null, Role.POSTULANTE);
        Postulante sinNombre = postulante(sinEmail, null);
        Postulante nombreEnBlanco = postulante(usuario("blanco@example.test", Role.POSTULANTE), "  ");
        when(postulantes.findPendientesDeAviso(any())).thenReturn(List.of(sinUsuario, sinNombre, nombreEnBlanco));
        when(postulantes.findPendientesDeOcultar(any())).thenReturn(List.of());
        when(mail.isConfigured()).thenReturn(true);

        RetencionPermanenciaService.RetencionRunResult resultado = service.procesar();

        assertThat(resultado.avisos()).isEqualTo(2);
        assertThat(resultado.mailsOk()).isEqualTo(1);
        assertThat(sinUsuario.getRetencionToken()).isNull();
        ArgumentCaptor<Notificacion> notificacion = ArgumentCaptor.forClass(Notificacion.class);
        verify(notificaciones, org.mockito.Mockito.times(2)).save(notificacion.capture());
        assertThat(notificacion.getAllValues())
                .allSatisfy(n -> assertThat(n.getCuerpo()).startsWith("Hola postulante,")
                        .contains("https://bolsa.test/permanencia?token="));
        ArgumentCaptor<MailTemplate.Content> correo = ArgumentCaptor.forClass(MailTemplate.Content.class);
        verify(mail).send(eq("blanco@example.test"), any(String.class), correo.capture());
        assertThat(correo.getValue().plain()).contains("Hola, postulante:")
                .contains("https://bolsa.test/permanencia?token=");
    }

    @Test
    void correoDeAvisoSinNombreUsaSaludoGenericoYFechaLimiteFormateada() {
        RetencionPermanenciaService service = service("https://bolsa.test");
        Postulante sinNombre = postulante(usuario("sin-nombre@example.test", Role.POSTULANTE), null);
        when(postulantes.findPendientesDeAviso(any())).thenReturn(List.of(sinNombre));
        when(postulantes.findPendientesDeOcultar(any())).thenReturn(List.of());
        when(mail.isConfigured()).thenReturn(true);

        assertThat(service.procesar().mailsOk()).isEqualTo(1);

        ArgumentCaptor<MailTemplate.Content> correo = ArgumentCaptor.forClass(MailTemplate.Content.class);
        verify(mail).send(eq("sin-nombre@example.test"), any(String.class), correo.capture());
        String fecha = sinNombre.getRetencionDeadlineAt().toLocalDate()
                .format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"));
        assertThat(correo.getValue().plain()).contains("Hola, postulante:").contains(fecha)
                .contains("https://bolsa.test/permanencia?token=" + sinNombre.getRetencionToken());
    }

    @Test
    void estadoSinAccionCuandoFaltaPlazoOElPlazoVencio() {
        RetencionPermanenciaService service = service("https://bolsa.test");
        Postulante postulante = postulante(usuario("ana@example.test", Role.POSTULANTE), "Ana");
        when(postulantes.findByUsuarioEmailIgnoreCase("ana@example.test")).thenReturn(Optional.of(postulante));

        postulante.setRetencionAvisadoAt(OffsetDateTime.now().minusDays(8));
        RetencionPermanenciaService.PermanenciaEstado sinPlazo = service.estado("ana@example.test");
        assertThat(sinPlazo.avisoPendiente()).isFalse();
        assertThat(sinPlazo.requiereAccion()).isFalse();

        postulante.setRetencionDeadlineAt(OffsetDateTime.now().minusHours(1));
        RetencionPermanenciaService.PermanenciaEstado vencido = service.estado("ana@example.test");
        assertThat(vencido.avisoPendiente()).isFalse();
        assertThat(vencido.ocultoPorRetencion()).isFalse();
        assertThat(vencido.deadline()).isEqualTo(postulante.getRetencionDeadlineAt());

        postulante.setVisible(false);
        postulante.setRetencionAvisadoAt(null);
        RetencionPermanenciaService.PermanenciaEstado ocultoManual = service.estado("ana@example.test");
        assertThat(ocultoManual.ocultoPorRetencion()).isFalse();
        assertThat(ocultoManual.requiereAccion()).isFalse();
    }

    @Test
    void confirmarPorTokenNuloEsSolicitudInvalida() {
        RetencionPermanenciaService service = service("https://bolsa.test");

        assertThatThrownBy(() -> service.confirmarPorToken(null))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void requierePostulanteConUsuarioActivoYRolPostulante() {
        RetencionPermanenciaService service = service("https://bolsa.test");
        Postulante sinUsuario = postulante(null, "Sin usuario");
        User baja = usuario("baja@example.test", Role.POSTULANTE);
        baja.setActivo(false);
        Postulante inactivo = postulante(baja, "Baja");
        Postulante socio = postulante(new User("socio@example.test", "123", "hash", Role.SOCIO), "Socio");
        when(postulantes.findByUsuarioEmailIgnoreCase("sin@example.test")).thenReturn(Optional.of(sinUsuario));
        when(postulantes.findByUsuarioEmailIgnoreCase("baja@example.test")).thenReturn(Optional.of(inactivo));
        when(postulantes.findByUsuarioEmailIgnoreCase("socio@example.test")).thenReturn(Optional.of(socio));

        for (String email : List.of("sin@example.test", "baja@example.test", "socio@example.test")) {
            assertThatThrownBy(() -> service.confirmarAutenticado(email))
                    .isInstanceOfSatisfying(ResponseStatusException.class,
                            e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
        }
        verify(postulantes, never()).save(any());
    }

    private RetencionPermanenciaService service(String webUrl) {
        return new RetencionPermanenciaService(postulantes, notificaciones, mail, webUrl);
    }

    private String cuerpoUltimaNotificacion() {
        ArgumentCaptor<Notificacion> captor = ArgumentCaptor.forClass(Notificacion.class);
        verify(notificaciones, org.mockito.Mockito.atLeastOnce()).save(captor.capture());
        return captor.getValue().getCuerpo();
    }

    private static User usuario(String email, Role role) {
        User user = new User(email, null, "hash", role);
        ReflectionTestUtils.setField(user, "id", (long) (email == null ? 0 : email.hashCode()));
        return user;
    }

    private static Postulante postulante(User user, String nombre) {
        return new Postulante(user, nombre, "12345678", "099123456", "San José");
    }
}

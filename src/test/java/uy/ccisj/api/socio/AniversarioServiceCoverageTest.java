package uy.ccisj.api.socio;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import uy.ccisj.api.mail.MailNotificationService;
import uy.ccisj.api.mail.MailTemplate;
import uy.ccisj.api.notificacion.Notificacion;
import uy.ccisj.api.notificacion.NotificacionRepository;
import uy.ccisj.api.user.User;

/** Casos de RF-15 no cubiertos por AniversarioServiceTest: fecha por defecto y resolución de email. */
class AniversarioServiceCoverageTest {
    private final SocioRepository socios = mock(SocioRepository.class);
    private final NotificacionRepository notificaciones = mock(NotificacionRepository.class);
    private final MailNotificationService mail = mock(MailNotificationService.class);
    private final AniversarioService service = new AniversarioService(socios, notificaciones, mail);

    private static Socio socio(String razonSocial, String emailContacto, User usuario) {
        Socio socio = mock(Socio.class);
        when(socio.getId()).thenReturn(1L);
        when(socio.getRazonSocial()).thenReturn(razonSocial);
        when(socio.getEmailContacto()).thenReturn(emailContacto);
        when(socio.getUsuario()).thenReturn(usuario);
        return socio;
    }

    private static User usuario(long id, String email) {
        User user = mock(User.class);
        when(user.getId()).thenReturn(id);
        when(user.isActivo()).thenReturn(true);
        when(user.getEmail()).thenReturn(email);
        return user;
    }

    @Test
    void sinFechaUsaElDiaActualDeMontevideo() {
        LocalDate hoy = LocalDate.now(ZoneId.of("America/Montevideo"));
        when(socios.findActivosWithAnniversaryOn(anyInt(), any())).thenReturn(List.of());

        AniversarioService.AniversarioRunResult result = service.procesarAniversariosDelDia(null);

        assertEquals(hoy, result.fecha());
        assertEquals(0, result.candidatos());
        verify(socios).findActivosWithAnniversaryOn(hoy.getMonthValue(),
                AniversarioService.anniversaryDaysFor(hoy));
    }

    @Test
    void contactoEnBlancoUsaEmailDeLaCuentaNormalizadoYSaludoGenerico() {
        Socio socio = socio("   ", "  ", usuario(10L, "  CUENTA@Example.COM "));
        when(socios.findActivosWithAnniversaryOn(anyInt(), any())).thenReturn(List.of(socio));
        when(mail.isConfigured()).thenReturn(true);

        AniversarioService.AniversarioRunResult result = service.procesarAniversariosDelDia(LocalDate.of(2026, 6, 1));

        assertEquals(1, result.mailsOk());
        ArgumentCaptor<MailTemplate.Content> contenido = ArgumentCaptor.forClass(MailTemplate.Content.class);
        verify(mail).send(eq("cuenta@example.com"), eq("CCISJ — " + AniversarioService.TITULO), contenido.capture());
        assertTrue(contenido.getValue().plain().contains("Hola, socio/a:"));
        ArgumentCaptor<Notificacion> campana = ArgumentCaptor.forClass(Notificacion.class);
        verify(notificaciones).save(campana.capture());
        assertTrue(campana.getValue().getCuerpo().contains("¡Feliz aniversario, socio/a!"));
    }

    @Test
    void emailDeContactoTienePrioridadSobreElDeLaCuentaYRazonSocialNulaUsaSaludoGenerico() {
        Socio socio = socio(null, " Contacto@Empresa.COM ", usuario(13L, "cuenta@example.com"));
        when(socios.findActivosWithAnniversaryOn(anyInt(), any())).thenReturn(List.of(socio));
        when(mail.isConfigured()).thenReturn(true);

        AniversarioService.AniversarioRunResult result = service.procesarAniversariosDelDia(LocalDate.of(2026, 6, 1));

        assertEquals(1, result.mailsOk());
        ArgumentCaptor<MailTemplate.Content> contenido = ArgumentCaptor.forClass(MailTemplate.Content.class);
        verify(mail).send(eq("contacto@empresa.com"), anyString(), contenido.capture());
        assertTrue(contenido.getValue().plain().contains("Hola, socio/a:"));
    }

    @Test
    void sinEmailDeContactoNiDeCuentaSoloQuedaLaCampana() {
        Socio sinEmailCuenta = socio("Uno", null, usuario(11L, null));
        Socio emailCuentaEnBlanco = socio("Dos", null, usuario(12L, "   "));
        when(socios.findActivosWithAnniversaryOn(anyInt(), any())).thenReturn(List.of(sinEmailCuenta, emailCuentaEnBlanco));
        when(mail.isConfigured()).thenReturn(true);

        AniversarioService.AniversarioRunResult result = service.procesarAniversariosDelDia(LocalDate.of(2026, 6, 1));

        assertEquals(2, result.campana());
        assertEquals(0, result.mailsOk());
        verify(mail, never()).send(anyString(), anyString(), any(MailTemplate.Content.class));
    }

    @Test
    void socioSinUsuarioSeOmite() {
        Socio sinUsuario = socio("Huérfano", "x@example.test", null);
        when(socios.findActivosWithAnniversaryOn(anyInt(), any())).thenReturn(List.of(sinUsuario));

        AniversarioService.AniversarioRunResult result = service.procesarAniversariosDelDia(LocalDate.of(2026, 6, 1));

        assertEquals(1, result.omitidos());
        assertEquals(0, result.campana());
        verifyNoInteractions(notificaciones, mail);
    }

    @Test
    void febreroDeAnioNoBisiestoDistintoDe28NoAgregaEl29() {
        assertEquals(List.of(27), AniversarioService.anniversaryDaysFor(LocalDate.of(2026, 2, 27)));
        assertEquals(List.of(28), AniversarioService.anniversaryDaysFor(LocalDate.of(2026, 3, 28)));
    }
}

package uy.ccisj.api.socio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import uy.ccisj.api.mail.MailNotificationService;
import uy.ccisj.api.notificacion.Notificacion;
import uy.ccisj.api.notificacion.NotificacionRepository;
import uy.ccisj.api.user.User;

class AniversarioServiceTest {
    private final SocioRepository socios = mock(SocioRepository.class);
    private final NotificacionRepository notificaciones = mock(NotificacionRepository.class);
    private final MailNotificationService mail = mock(MailNotificationService.class);
    private final AniversarioService service = new AniversarioService(socios, notificaciones, mail);

    @Test
    void febreroNoBisiestoIncluyeAniversariosDel29() {
        assertEquals(List.of(28, 29), AniversarioService.anniversaryDaysFor(LocalDate.of(2026, 2, 28)));
        assertEquals(List.of(28), AniversarioService.anniversaryDaysFor(LocalDate.of(2028, 2, 28)));
        assertEquals(List.of(1), AniversarioService.anniversaryDaysFor(LocalDate.of(2026, 3, 1)));
    }

    @Test
    void omiteCuentaInactivaYNotificacionDuplicadaDelMismoDia() {
        LocalDate date = LocalDate.of(2026, 11, 21);
        Socio inactive = socio(1L, false, "Inactivo", "inactivo@example.com", false);
        Socio duplicate = socio(2L, true, "Duplicado", "duplicado@example.com", false);
        when(socios.findActivosWithAnniversaryOn(11, List.of(21))).thenReturn(List.of(inactive, duplicate));
        when(notificaciones.existsByUsuarioIdAndTituloAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                any(), anyString(), any(), any())).thenAnswer(invocation -> invocation.getArgument(0).equals(2L));

        AniversarioService.AniversarioRunResult result = service.procesarAniversariosDelDia(date);

        assertEquals(2, result.candidatos());
        assertEquals(0, result.campana());
        assertEquals(2, result.omitidos());
        verify(notificaciones, never()).save(any());
        verify(mail, never()).send(anyString(), anyString(), any());
    }

    @Test
    void creaCampanaYUsaEmailDeContactoNormalizado() {
        LocalDate date = LocalDate.of(2026, 11, 21);
        Socio socio = socio(3L, true, " Empresa ", " CONTACTO@EXAMPLE.COM ", false);
        when(socios.findActivosWithAnniversaryOn(anyInt(), any())).thenReturn(List.of(socio));
        when(mail.isConfigured()).thenReturn(true);

        AniversarioService.AniversarioRunResult result = service.procesarAniversariosDelDia(date);

        assertEquals(1, result.campana());
        assertEquals(1, result.mailsOk());
        assertEquals(0, result.mailsFail());
        verify(notificaciones).save(any(Notificacion.class));
        verify(mail).send(org.mockito.ArgumentMatchers.eq("contacto@example.com"), anyString(), any());
    }

    @Test
    void campanaPermaneceAunqueCorreoEsteSilenciadoSinEmailOSmtp() {
        LocalDate date = LocalDate.of(2026, 11, 21);
        Socio silenced = socio(4L, true, "Silenciado", "silenced@example.com", true);
        Socio withoutEmail = socio(5L, true, "Sin email", null, false);
        Socio withoutSmtp = socio(6L, true, null, "cuenta@example.com", false);
        when(socios.findActivosWithAnniversaryOn(anyInt(), any()))
                .thenReturn(List.of(silenced, withoutEmail, withoutSmtp));
        when(mail.isConfigured()).thenReturn(false);

        AniversarioService.AniversarioRunResult result = service.procesarAniversariosDelDia(date);

        assertEquals(3, result.campana());
        assertEquals(0, result.mailsOk());
        assertEquals(0, result.mailsFail());
        verify(mail, never()).send(anyString(), anyString(), any());
    }

    @Test
    void falloDeCorreoNoRevierteLaCampana() {
        LocalDate date = LocalDate.of(2026, 11, 21);
        Socio socio = socio(7L, true, "Empresa", "mail@example.com", false);
        when(socios.findActivosWithAnniversaryOn(anyInt(), any())).thenReturn(List.of(socio));
        when(mail.isConfigured()).thenReturn(true);
        org.mockito.Mockito.doThrow(new IllegalStateException("smtp"))
                .when(mail).send(anyString(), anyString(), any());

        AniversarioService.AniversarioRunResult result = service.procesarAniversariosDelDia(date);

        assertEquals(1, result.campana());
        assertEquals(0, result.mailsOk());
        assertEquals(1, result.mailsFail());
    }

    private static Socio socio(
            Long userId,
            boolean active,
            String businessName,
            String accountEmail,
            boolean muteEmails) {
        User user = mock(User.class);
        when(user.getId()).thenReturn(userId);
        when(user.isActivo()).thenReturn(active);
        when(user.getEmail()).thenReturn(accountEmail);
        Socio socio = mock(Socio.class);
        when(socio.getId()).thenReturn(userId);
        when(socio.getUsuario()).thenReturn(user);
        when(socio.getRazonSocial()).thenReturn(businessName);
        when(socio.isSilenciarCorreos()).thenReturn(muteEmails);
        return socio;
    }
}

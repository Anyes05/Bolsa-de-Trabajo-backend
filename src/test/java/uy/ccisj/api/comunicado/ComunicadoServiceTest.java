package uy.ccisj.api.comunicado;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.web.server.ResponseStatusException;
import uy.ccisj.api.comunicado.dto.ComunicadoCreateDTO;
import uy.ccisj.api.comunicado.dto.ComunicadoResponseDTO;
import uy.ccisj.api.domain.DestinatarioComunicado;
import uy.ccisj.api.domain.EstadoMorosidad;
import uy.ccisj.api.mail.MailNotificationService;
import uy.ccisj.api.mail.MailTemplate;
import uy.ccisj.api.notificacion.Notificacion;
import uy.ccisj.api.notificacion.NotificacionRepository;
import uy.ccisj.api.socio.Socio;
import uy.ccisj.api.socio.SocioRepository;
import uy.ccisj.api.user.Role;
import uy.ccisj.api.user.User;
import uy.ccisj.api.user.UserRepository;

class ComunicadoServiceTest {
    private final ComunicadoRepository comunicados = mock(ComunicadoRepository.class);
    private final NotificacionRepository notificaciones = mock(NotificacionRepository.class);
    private final SocioRepository socios = mock(SocioRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final MailNotificationService mail = mock(MailNotificationService.class);
    private final PlatformTransactionManager transactions = mock(PlatformTransactionManager.class);
    private ComunicadoService service;

    @BeforeEach
    void setUp() {
        when(transactions.getTransaction(any())).thenReturn(mock(TransactionStatus.class));
        when(comunicados.save(any(Comunicado.class))).thenAnswer(invocation -> invocation.getArgument(0));
        service = new ComunicadoService(comunicados, notificaciones, socios, users, mail, transactions);
    }

    @Test
    void cuentaAudienciasYListaComunicadosSinEstadisticasInventadas() {
        when(socios.countActivos(EstadoMorosidad.INACTIVO)).thenReturn(12L);
        when(socios.countDirectivosActivos(EstadoMorosidad.INACTIVO)).thenReturn(3L);
        User author = user("admin@example.com", Role.ADMIN, true);
        Comunicado comunicado = new Comunicado(
                author, "Aviso", "Contenido", DestinatarioComunicado.SOCIOS, true);
        when(comunicados.findAllByOrderByFechaPublicacionDesc()).thenReturn(List.of(comunicado));

        assertEquals(12L, service.audienciaCounts().sociosActivos());
        assertEquals(3L, service.audienciaCounts().sociosDirectivos());
        ComunicadoResponseDTO response = service.list().getFirst();
        assertEquals("Aviso", response.titulo());
        assertEquals("admin@example.com", response.autorEmail());
        assertEquals(0, response.correosEnviados());
    }

    @Test
    void publicaASociosDeduplicandoEmailsYRespetandoSilenciamiento() {
        User admin = user("admin@example.com", Role.ADMIN, true);
        when(users.findByEmailIgnoreCase("admin@example.com")).thenReturn(Optional.of(admin));
        Socio contact = socio(user("cuenta1@example.com", Role.SOCIO, true), "Empresa 1");
        contact.setEmailContacto(" CONTACTO@EXAMPLE.COM ");
        Socio duplicate = socio(user("contacto@example.com", Role.SOCIO, true), "Empresa 2");
        Socio muted = socio(user("silenciado@example.com", Role.SOCIO, true), "Empresa 3");
        muted.setSilenciarCorreos(true);
        Socio withoutEmail = socio(user(null, Role.SOCIO, true), "Empresa 4");
        Socio inactive = socio(user("inactivo@example.com", Role.SOCIO, false), "Empresa 5");
        when(socios.findActivosWithUsuario())
                .thenReturn(List.of(contact, duplicate, muted, withoutEmail, inactive));

        ComunicadoResponseDTO response = service.publish(
                "admin@example.com",
                new ComunicadoCreateDTO(
                        "  Aviso general  ", "  Contenido  ", DestinatarioComunicado.TODOS, true));

        assertEquals(DestinatarioComunicado.SOCIOS, response.destinatario());
        assertEquals("Aviso general", response.titulo());
        assertEquals(4, response.destinatariosNotificados());
        assertEquals(1, response.correosEnviados());
        assertEquals(1, response.sociosSinEmail());
        assertEquals(List.of("contacto@example.com"), response.correosDestino());
        verify(notificaciones).saveAll(any());
        verify(mail).send(
                org.mockito.ArgumentMatchers.eq("contacto@example.com"),
                org.mockito.ArgumentMatchers.eq("CCISJ — Aviso general"),
                any(MailTemplate.Content.class));
    }

    @Test
    void audienciaDirectivaUsaRepositorioEspecificoYAislaFalloDeCorreo() {
        User admin = user("admin@example.com", Role.ADMIN, true);
        when(users.findByEmailIgnoreCase("admin@example.com")).thenReturn(Optional.of(admin));
        Socio director = socio(user("director@example.com", Role.SOCIO, true), "Directivo");
        when(socios.findDirectivosActivosWithUsuario()).thenReturn(List.of(director));
        doThrow(new IllegalStateException("smtp"))
                .when(mail).send(anyString(), anyString(), any(MailTemplate.Content.class));

        ComunicadoResponseDTO response = service.publish(
                "admin@example.com",
                new ComunicadoCreateDTO(
                        "Directivos", "Mensaje", DestinatarioComunicado.SOLO_DIRECTIVOS, false));

        assertEquals(1, response.destinatariosNotificados());
        assertEquals(0, response.correosEnviados());
        assertEquals(1, response.correosFallidos());
        verify(socios, never()).findActivosWithUsuario();
    }

    @Test
    void exigeAdministradorActivoYTextosValidos() {
        User socio = user("socio@example.com", Role.SOCIO, true);
        when(users.findByEmailIgnoreCase("socio@example.com")).thenReturn(Optional.of(socio));

        assertStatus(
                HttpStatus.FORBIDDEN,
                () -> service.publish(
                        "socio@example.com",
                        new ComunicadoCreateDTO("Aviso", "Contenido", DestinatarioComunicado.SOCIOS, false)));

        User admin = user("admin@example.com", Role.ADMIN, true);
        when(users.findByEmailIgnoreCase("admin@example.com")).thenReturn(Optional.of(admin));
        assertStatus(
                HttpStatus.BAD_REQUEST,
                () -> service.publish(
                        "admin@example.com",
                        new ComunicadoCreateDTO(" ", "Contenido", DestinatarioComunicado.SOCIOS, false)));
        assertStatus(
                HttpStatus.BAD_REQUEST,
                () -> service.publish(
                        "admin@example.com",
                        new ComunicadoCreateDTO("x".repeat(181), "Contenido", DestinatarioComunicado.SOCIOS, false)));
        assertStatus(
                HttpStatus.BAD_REQUEST,
                () -> service.publish(
                        "admin@example.com",
                        new ComunicadoCreateDTO("Aviso", null, DestinatarioComunicado.SOCIOS, false)));
    }

    @Test
    void rechazaAudienciaNoHabilitadaYSinDestinatarios() {
        User admin = user("admin@example.com", Role.ADMIN, true);
        when(users.findByEmailIgnoreCase("admin@example.com")).thenReturn(Optional.of(admin));

        assertStatus(
                HttpStatus.BAD_REQUEST,
                () -> service.publish(
                        "admin@example.com",
                        new ComunicadoCreateDTO(
                                "Aviso", "Contenido", DestinatarioComunicado.POSTULANTES, false)));

        when(socios.findActivosWithUsuario()).thenReturn(List.of());
        assertStatus(
                HttpStatus.BAD_REQUEST,
                () -> service.publish(
                        "admin@example.com",
                        new ComunicadoCreateDTO(
                                "Aviso", "Contenido", DestinatarioComunicado.SOCIOS, false)));
    }

    @Test
    void rechazaAudienciaSiNingunSocioTieneCuentaActiva() {
        User admin = user("admin@example.com", Role.ADMIN, true);
        when(users.findByEmailIgnoreCase("admin@example.com")).thenReturn(Optional.of(admin));
        Socio withoutAccount = socio(null, "Sin cuenta");
        Socio inactive = socio(user("inactivo@example.com", Role.SOCIO, false), "Inactivo");
        when(socios.findActivosWithUsuario()).thenReturn(List.of(withoutAccount, inactive));

        assertStatus(
                HttpStatus.BAD_REQUEST,
                () -> service.publish(
                        "admin@example.com",
                        new ComunicadoCreateDTO(
                                "Aviso", "Contenido", DestinatarioComunicado.SOCIOS, false)));
        verify(notificaciones, never()).saveAll(any());
    }

    @Test
    void resuelveEmailDeContactoCuentaOAusencia() {
        Socio contact = socio(user("cuenta@example.com", Role.SOCIO, true), "Contacto");
        contact.setEmailContacto(" CONTACTO@EXAMPLE.COM ");
        Socio account = socio(user(" CUENTA@EXAMPLE.COM ", Role.SOCIO, true), "Cuenta");
        Socio none = socio(user(" ", Role.SOCIO, true), "Sin correo");

        assertEquals("contacto@example.com", ComunicadoService.resolveContactEmail(contact));
        assertEquals("cuenta@example.com", ComunicadoService.resolveContactEmail(account));
        assertEquals(null, ComunicadoService.resolveContactEmail(none));
    }

    private static User user(String email, Role role, boolean active) {
        User user = new User(email, null, "hash", role);
        user.setActivo(active);
        return user;
    }

    private static Socio socio(User user, String name) {
        return new Socio(user, name);
    }

    private static void assertStatus(HttpStatus expected, Runnable action) {
        ResponseStatusException error = assertThrows(ResponseStatusException.class, action::run);
        assertEquals(expected, error.getStatusCode());
    }
}

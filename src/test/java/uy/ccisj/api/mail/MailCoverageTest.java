package uy.ccisj.api.mail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.server.ResponseStatusException;
import uy.ccisj.api.user.Role;
import uy.ccisj.api.user.User;
import uy.ccisj.api.user.UserRepository;

class MailCoverageTest {
    private final JavaMailSender sender = mock(JavaMailSender.class);
    private MailNotificationService service;

    @BeforeEach
    void setUp() {
        when(sender.createMimeMessage()).thenAnswer(invocation -> new MimeMessage(Session.getInstance(new Properties())));
        service = new MailNotificationService(sender, "from@x.test", " admin@x.test ", "usuario", "clave");
    }

    @Test
    void parametrosNulosOVaciosDejanElServicioSinConfigurar() {
        MailNotificationService nulos = new MailNotificationService(sender, null, null, null, null);
        assertThat(nulos.isConfigured()).isFalse();
        assertStatus(HttpStatus.SERVICE_UNAVAILABLE, nulos::requireConfigured);
        assertStatus(HttpStatus.SERVICE_UNAVAILABLE, () -> nulos.send("a@x.test", "Asunto", "Texto"));

        assertThat(new MailNotificationService(sender, "f@x.test", "", " ", "clave").isConfigured()).isFalse();
        assertThat(new MailNotificationService(sender, "f@x.test", "", "u", "  ").isConfigured()).isFalse();
        assertThat(service.isConfigured()).isTrue();
        verify(sender, never()).send(any(MimeMessage.class));
    }

    @Test
    void rechazaDestinatarioVacioYContenidoNulo() {
        assertStatus(HttpStatus.BAD_REQUEST, () -> service.send(null, "Asunto", "Texto"));
        assertStatus(HttpStatus.BAD_REQUEST, () -> service.send("  ", "Asunto", "Texto"));
        assertStatus(HttpStatus.BAD_REQUEST, () -> service.send("a@x.test", "Asunto", (MailTemplate.Content) null));
        verify(sender, never()).send(any(MimeMessage.class));
    }

    @Test
    void enviaTextoPlanoNuloYContenidoConHtmlVacioComoTextoSimple() throws Exception {
        service.send("a@x.test", "Asunto", (String) null, "  ");
        service.send("b@x.test", "Asunto 2", new MailTemplate.Content(" ", null), " reply@x.test ");

        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(sender, org.mockito.Mockito.times(2)).send(captor.capture());
        MimeMessage primero = captor.getAllValues().get(0);
        assertThat(primero.getContent()).isEqualTo("");
        assertThat(primero.getReplyTo()[0].toString()).isEqualTo("from@x.test");
        MimeMessage segundo = captor.getAllValues().get(1);
        assertThat(segundo.getContent()).isEqualTo("");
        assertThat(((InternetAddress) segundo.getReplyTo()[0]).getAddress()).isEqualTo("reply@x.test");
    }

    @Test
    void enviaHtmlConTextoPlanoNuloComoMultipart() throws Exception {
        service.send("a@x.test", "Asunto", new MailTemplate.Content("<p>Hola</p>", null));

        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(sender).send(captor.capture());
        assertThat(captor.getValue().getContent()).isInstanceOf(MimeMultipart.class);
    }

    @Test
    void envioAlAdministradorUsaElDestinatarioConfigurado() throws Exception {
        service.sendToAdmin("Aviso", MailTemplate.simple("Título", "Hola", "Párrafo"));

        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(sender).send(captor.capture());
        assertThat(((InternetAddress) captor.getValue().getAllRecipients()[0]).getAddress()).isEqualTo("admin@x.test");
        assertThat(captor.getValue().getSubject()).isEqualTo("Aviso");
    }

    @Test
    void propagaResponseStatusExceptionDelEnvioSinEnvolverla() {
        ResponseStatusException original = new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Límite");
        doThrow(original).when(sender).send(any(MimeMessage.class));

        assertThatThrownBy(() -> service.send("a@x.test", "Asunto", "Texto")).isSameAs(original);
    }

    @Test
    void fallaSmtpSinMensajeUsaElNombreDeLaExcepcionRaiz() {
        doThrow(new MailSendException("envoltorio", new IllegalStateException()))
                .when(sender).send(any(MimeMessage.class));
        assertThatThrownBy(() -> service.send("a@x.test", "Asunto", "Texto"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("IllegalStateException")
                .satisfies(error -> assertThat(((ResponseStatusException) error).getStatusCode())
                        .isEqualTo(HttpStatus.BAD_GATEWAY));

        doThrow(new MailSendException("envoltorio", new IllegalStateException(" ")))
                .when(sender).send(any(MimeMessage.class));
        assertThatThrownBy(() -> service.send("a@x.test", "Asunto", "Texto"))
                .hasMessageContaining("IllegalStateException");
    }

    @Test
    void sendToManyIgnoraNulosVaciosYDuplicados() {
        List<String> destinatarios = new ArrayList<>(Arrays.asList("a@x.test", null, " ", "a@x.test", "b@x.test"));
        service.sendToMany(destinatarios, "Asunto", "Texto");
        verify(sender, org.mockito.Mockito.times(2)).send(any(MimeMessage.class));
    }

    @Test
    void adminActivoEnviaCorreoDePruebaYLosDemasSonRechazados() {
        MailNotificationService mail = mock(MailNotificationService.class);
        UserRepository usuarios = mock(UserRepository.class);
        AdminMailController controller = new AdminMailController(mail, usuarios);
        User admin = usuario(Role.ADMIN, true);
        User inactivo = usuario(Role.ADMIN, false);
        User socio = usuario(Role.SOCIO, true);
        when(usuarios.findByEmailIgnoreCase("admin@x.test")).thenReturn(Optional.of(admin));
        when(usuarios.findByEmailIgnoreCase("inactivo@x.test")).thenReturn(Optional.of(inactivo));
        when(usuarios.findByEmailIgnoreCase("socio@x.test")).thenReturn(Optional.of(socio));
        when(usuarios.findByEmailIgnoreCase("nadie@x.test")).thenReturn(Optional.empty());

        for (String email : List.of("inactivo@x.test", "socio@x.test", "nadie@x.test")) {
            assertStatus(HttpStatus.FORBIDDEN,
                    () -> controller.sendTest(new UsernamePasswordAuthenticationToken(email, null)));
        }
        verifyNoInteractions(mail);

        controller.sendTest(new UsernamePasswordAuthenticationToken("admin@x.test", null));

        ArgumentCaptor<MailTemplate.Content> contenido = ArgumentCaptor.forClass(MailTemplate.Content.class);
        verify(mail).sendToAdmin(eq("Prueba SMTP CCISJ"), contenido.capture());
        assertThat(contenido.getValue().plain())
                .contains("Prueba de correo", "Hola,", "configurado correctamente", "Gmail SMTP funciona bien");
    }

    @Test
    void plantillaToleraListasNulasElementosVaciosYCamposAusentes() {
        MailTemplate.Content content = MailTemplate.build(null, null, null, null, null, null);

        assertThat(content.html()).contains("<title></title>")
                .contains("Este es un correo automático del sistema de Bolsa de Trabajo del CCISJ.");
        assertThat(content.plain()).startsWith("Centro Comercial e Industrial de San José (CCISJ)\n\n\n---")
                .contains("Este es un correo automático");
        assertThat(MailTemplate.escape(null)).isEmpty();
    }

    @Test
    void plantillaFiltraParrafosYDetallesVaciosYUsaEtiquetaPorDefectoDelBoton() {
        MailTemplate.Content content = MailTemplate.build(
                " Título ",
                "  ",
                Arrays.asList("Uno", null, " "),
                Arrays.asList(new MailTemplate.Detail("Nulo", null), null,
                        new MailTemplate.Detail("Vacío", " "), new MailTemplate.Detail("Clave", "Valor")),
                new MailTemplate.Cta(null, "https://x.test/?a=1&b='2'"),
                " Nota al pie ");

        assertThat(content.html()).contains("Abrir enlace").contains("&#39;2&#39;").contains("Clave")
                .doesNotContain("Nulo").doesNotContain("Vacío");
        assertThat(content.plain()).contains("Título\n\nUno\n\nClave: Valor\n\nhttps://x.test/?a=1&b='2'\n\n---\nNota al pie")
                .doesNotContain("Abrir enlace");

        MailTemplate.Content etiquetaVacia = MailTemplate.build("T", null, List.of(), List.of(),
                new MailTemplate.Cta(" ", "https://x.test"), " ");
        assertThat(etiquetaVacia.html()).contains("Abrir enlace");
        assertThat(etiquetaVacia.plain()).doesNotContain(" :").contains("https://x.test");
    }

    @Test
    void plantillaOmiteBotonSinUrl() {
        MailTemplate.Content sinUrl = MailTemplate.build("T", null, List.of("P"), List.of(),
                new MailTemplate.Cta("Ir", null), null);
        MailTemplate.Content urlVacia = MailTemplate.build("T", null, List.of("P"), List.of(),
                new MailTemplate.Cta("Ir", "  "), null);

        assertThat(sinUrl.html()).doesNotContain("<a href");
        assertThat(sinUrl.plain()).doesNotContain("Ir:");
        assertThat(urlVacia.html()).doesNotContain("<a href");
        assertThat(urlVacia.plain()).doesNotContain("Ir:");
    }

    private static User usuario(Role rol, boolean activo) {
        User usuario = mock(User.class);
        when(usuario.getRole()).thenReturn(rol);
        when(usuario.isActivo()).thenReturn(activo);
        return usuario;
    }

    private static void assertStatus(HttpStatus esperado, Runnable accion) {
        assertThatThrownBy(accion::run)
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(error -> assertThat(((ResponseStatusException) error).getStatusCode()).isEqualTo(esperado));
    }
}

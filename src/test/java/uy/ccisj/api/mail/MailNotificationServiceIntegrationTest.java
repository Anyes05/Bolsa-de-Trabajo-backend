package uy.ccisj.api.mail;

import static org.junit.jupiter.api.Assertions.*;

import com.icegreen.greenmail.util.GreenMail;
import com.icegreen.greenmail.util.ServerSetup;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.web.server.ResponseStatusException;

class MailNotificationServiceIntegrationTest {
    private GreenMail smtp;
    private JavaMailSenderImpl sender;
    private MailNotificationService service;

    @BeforeEach
    void startSmtp() {
        smtp = new GreenMail(new ServerSetup(0, "127.0.0.1", ServerSetup.PROTOCOL_SMTP));
        smtp.start();
        smtp.setUser("sender@example.test", "sender", "test-password");
        sender = new JavaMailSenderImpl();
        sender.setHost("127.0.0.1");
        sender.setPort(smtp.getSmtp().getPort());
        sender.setUsername("sender");
        sender.setPassword("test-password");
        sender.getJavaMailProperties().setProperty("mail.smtp.auth", "true");
        sender.getJavaMailProperties().setProperty("mail.smtp.connectiontimeout", "2000");
        sender.getJavaMailProperties().setProperty("mail.smtp.timeout", "2000");
        sender.getJavaMailProperties().setProperty("mail.smtp.writetimeout", "2000");
        service = new MailNotificationService(sender, " sender@example.test ", "admin@example.test",
                "sender", "test-password");
    }

    @AfterEach
    void stopSmtp() {
        smtp.stop();
    }

    @Test
    void sendsUtf8PlainTextWithEnvelopeHeadersAndReplyTo() throws Exception {
        service.send(" recipient@example.test ", "Inscripción — José", "¡Hola, José!\nCuota: $100.",
                " reply@example.test ");

        assertTrue(smtp.waitForIncomingEmail(2000, 1));
        MimeMessage message = smtp.getReceivedMessages()[0];
        assertEquals(1, smtp.getReceivedMessages().length);
        assertEquals("sender@example.test", ((InternetAddress) message.getFrom()[0]).getAddress());
        assertEquals("recipient@example.test", ((InternetAddress) message.getAllRecipients()[0]).getAddress());
        assertEquals("reply@example.test", ((InternetAddress) message.getReplyTo()[0]).getAddress());
        assertEquals("Inscripción — José", message.getSubject());
        assertTrue(message.isMimeType("text/plain"));
        assertEquals("¡Hola, José!\nCuota: $100.", message.getContent().toString().replace("\r\n", "\n"));
    }

    @Test
    void sendsHtmlAndPlainAlternativesWithLink() throws Exception {
        String html = "<p>José: <a href=\"https://example.test/ofertas/42\">Ver oferta</a></p>";
        String plain = "José: https://example.test/ofertas/42";
        service.send("recipient@example.test", "Oferta", new MailTemplate.Content(html, plain));

        assertTrue(smtp.waitForIncomingEmail(2000, 1));
        MimeMessage message = smtp.getReceivedMessages()[0];
        assertTrue(message.isMimeType("multipart/*"));
        List<Part> parts = new ArrayList<>();
        collectLeafParts(message, parts);
        assertEquals(2, parts.size());
        assertEquals(plain, parts.stream().filter(part -> isMimeType(part, "text/plain"))
                .findFirst().orElseThrow().getContent());
        assertEquals(html, parts.stream().filter(part -> isMimeType(part, "text/html"))
                .findFirst().orElseThrow().getContent());
    }

    @Test
    void sendsToAdminAndDeduplicatesNonBlankRecipients() throws Exception {
        service.sendToAdmin("Administración", "Aviso");
        service.sendToMany(Arrays.asList(null, "", " ", "one@example.test", "one@example.test",
                "two@example.test"), "Comunicado", "Texto");

        assertTrue(smtp.waitForIncomingEmail(2000, 3));
        MimeMessage[] messages = smtp.getReceivedMessages();
        assertEquals(3, messages.length);
        List<String> recipients = new ArrayList<>();
        for (MimeMessage message : messages) {
            assertEquals(1, message.getAllRecipients().length);
            recipients.add(((InternetAddress) message.getAllRecipients()[0]).getAddress());
        }
        assertEquals(List.of("admin@example.test", "one@example.test", "two@example.test"),
                recipients.stream().sorted().toList());
        assertEquals("Administración", messages[0].getSubject());
    }

    @Test
    void rejectsMissingRecipientContentOrConfigurationWithoutSending() {
        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ResponseStatusException.class,
                () -> service.send(" ", "Asunto", "Texto")).getStatusCode());
        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ResponseStatusException.class,
                () -> service.send("recipient@example.test", "Asunto", (MailTemplate.Content) null)).getStatusCode());
        var unconfigured = new MailNotificationService(sender, "", "admin@example.test", "sender", "");
        assertFalse(unconfigured.isConfigured());
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, assertThrows(ResponseStatusException.class,
                () -> unconfigured.send("recipient@example.test", "Asunto", "Texto")).getStatusCode());
        assertEquals(0, smtp.getReceivedMessages().length);
    }

    @Test
    void translatesRealSmtpAuthenticationFailureToBadGateway() {
        sender.setPassword("incorrect-password");
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.send("recipient@example.test", "Asunto", "Texto"));
        assertEquals(HttpStatus.BAD_GATEWAY, error.getStatusCode());
        assertNotNull(error.getCause());
        assertEquals(0, smtp.getReceivedMessages().length);
    }

    private static void collectLeafParts(Part part, List<Part> leaves) throws Exception {
        if (part.getContent() instanceof Multipart multipart) {
            for (int i = 0; i < multipart.getCount(); i++) {
                collectLeafParts(multipart.getBodyPart(i), leaves);
            }
        } else {
            leaves.add(part);
        }
    }

    private static boolean isMimeType(Part part, String type) {
        try {
            return part.isMimeType(type);
        } catch (jakarta.mail.MessagingException exception) {
            throw new AssertionError(exception);
        }
    }
}

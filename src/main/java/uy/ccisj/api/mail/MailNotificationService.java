package uy.ccisj.api.mail;

import java.util.Collection;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class MailNotificationService {
    private static final Logger LOGGER = LoggerFactory.getLogger(MailNotificationService.class);

    private final JavaMailSender mailSender;
    private final String from;
    private final String adminRecipient;
    private final String username;
    private final String password;

    public MailNotificationService(
            JavaMailSender mailSender,
            @Value("${app.mail.from:}") String from,
            @Value("${app.mail.admin-recipient:}") String adminRecipient,
            @Value("${spring.mail.username:}") String username,
            @Value("${spring.mail.password:}") String password) {
        this.mailSender = mailSender;
        this.from = from == null ? "" : from.trim();
        this.adminRecipient = adminRecipient == null ? "" : adminRecipient.trim();
        this.username = username == null ? "" : username.trim();
        this.password = password == null ? "" : password.trim();
    }

    public boolean isConfigured() {
        return !from.isBlank() && !username.isBlank() && !password.isBlank();
    }

    public void requireConfigured() {
        if (!isConfigured()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "El envío por Gmail no está configurado (MAIL_FROM / GMAIL_SMTP_USERNAME / GMAIL_SMTP_APP_PASSWORD)");
        }
    }

    /** Compatibilidad: envía texto plano. Preferir {@link #send(String, String, MailTemplate.Content)}. */
    public void send(String recipient, String subject, String text) {
        send(recipient, subject, text, null);
    }

    public void send(String recipient, String subject, String text, String replyTo) {
        sendInternal(recipient, subject, text, null, replyTo);
    }

    public void send(String recipient, String subject, MailTemplate.Content content) {
        send(recipient, subject, content, null);
    }

    public void send(String recipient, String subject, MailTemplate.Content content, String replyTo) {
        if (content == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El contenido del correo es obligatorio");
        }
        sendInternal(recipient, subject, content.plain(), content.html(), replyTo);
    }

    public void sendToMany(Collection<String> recipients, String subject, String text) {
        recipients.stream().filter(recipient -> recipient != null && !recipient.isBlank())
                .distinct().forEach(recipient -> send(recipient, subject, text));
    }

    public void sendToAdmin(String subject, String text) {
        send(adminRecipient, subject, text);
    }

    public void sendToAdmin(String subject, MailTemplate.Content content) {
        send(adminRecipient, subject, content);
    }

    private void sendInternal(String recipient, String subject, String plain, String html, String replyTo) {
        if (recipient == null || recipient.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El destinatario del correo es obligatorio");
        }
        requireConfigured();
        try {
            boolean multipart = html != null && !html.isBlank();
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, multipart, "UTF-8");
            helper.setFrom(from);
            helper.setTo(recipient.trim());
            helper.setSubject(subject);
            if (multipart) {
                helper.setText(plain == null ? "" : plain, html);
            } else {
                helper.setText(plain == null ? "" : plain, false);
            }
            if (replyTo != null && !replyTo.isBlank()) {
                helper.setReplyTo(replyTo.trim());
            }
            mailSender.send(message);
            LOGGER.info("Correo enviado a {}", recipient.trim());
        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (Exception exception) {
            LOGGER.error("Fallo SMTP al enviar a {}: {}", recipient, exception.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "No se pudo enviar el correo mediante SMTP: " + rootMessage(exception), exception);
        }
    }

    private static String rootMessage(Throwable exception) {
        Throwable current = exception;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        String message = current.getMessage();
        return message == null || message.isBlank() ? current.getClass().getSimpleName() : message;
    }
}

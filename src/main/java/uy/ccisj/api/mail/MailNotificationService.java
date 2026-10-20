package uy.ccisj.api.mail;

import java.util.Collection;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class MailNotificationService {
    private final JavaMailSender mailSender;
    private final String from;
    private final String adminRecipient;

    public MailNotificationService(JavaMailSender mailSender, @Value("${app.mail.from:}") String from,
            @Value("${app.mail.admin-recipient:}") String adminRecipient) {
        this.mailSender = mailSender;
        this.from = from;
        this.adminRecipient = adminRecipient;
    }

    public void send(String recipient, String subject, String text) {
        if (recipient == null || recipient.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El destinatario del correo es obligatorio");
        }
        if (from.isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "SMTP no está configurado");
        }
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(from);
            message.setTo(recipient.trim());
            message.setSubject(subject);
            message.setText(text);
            mailSender.send(message);
        } catch (RuntimeException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "No se pudo enviar el correo mediante SMTP", exception);
        }
    }

    public void sendToMany(Collection<String> recipients, String subject, String text) {
        recipients.stream().filter(recipient -> recipient != null && !recipient.isBlank())
                .distinct().forEach(recipient -> send(recipient, subject, text));
    }

    public void sendToAdmin(String subject, String text) {
        send(adminRecipient, subject, text);
    }
}
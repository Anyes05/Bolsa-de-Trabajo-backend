package uy.ccisj.api.mail;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import uy.ccisj.api.user.Role;
import uy.ccisj.api.user.UserRepository;

@RestController
@RequestMapping("/admin/correo")
public class AdminMailController {
    private final MailNotificationService mailNotificationService;
    private final UserRepository userRepository;

    public AdminMailController(MailNotificationService mailNotificationService, UserRepository userRepository) {
        this.mailNotificationService = mailNotificationService;
        this.userRepository = userRepository;
    }

    @PostMapping("/prueba")
    public void sendTest(Authentication authentication) {
        boolean isAdmin = userRepository.findByEmailIgnoreCase(authentication.getName())
                .filter(user -> user.isActivo() && user.getRole() == Role.ADMIN)
                .isPresent();
        if (!isAdmin) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Se requiere una cuenta de administrador activa");
        }
        mailNotificationService.sendToAdmin("Prueba SMTP CCISJ",
                "El servicio SMTP de CCISJ se configuró correctamente.");
    }
}
package uy.ccisj.api.socio;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/aniversarios")
public class AdminAniversarioController {
    private final AniversarioService aniversarioService;

    public AdminAniversarioController(AniversarioService aniversarioService) {
        this.aniversarioService = aniversarioService;
    }

    /** Disparo manual (ops / E2E). El cron diario sigue en {@link AniversarioScheduler}. */
    @PostMapping("/ejecutar")
    public AniversarioService.AniversarioRunResult ejecutar() {
        return aniversarioService.procesarAniversariosDelDia(null);
    }
}

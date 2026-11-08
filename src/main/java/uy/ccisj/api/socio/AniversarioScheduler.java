package uy.ccisj.api.socio;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class AniversarioScheduler {
    private static final Logger LOGGER = LoggerFactory.getLogger(AniversarioScheduler.class);

    private final AniversarioService aniversarioService;

    public AniversarioScheduler(AniversarioService aniversarioService) {
        this.aniversarioService = aniversarioService;
    }

    /** Todos los días a las 08:00 hora de Uruguay. */
    @Scheduled(cron = "0 0 8 * * *", zone = "America/Montevideo")
    public void enviarAniversariosDelDia() {
        try {
            AniversarioService.AniversarioRunResult result = aniversarioService.procesarAniversariosDelDia(null);
            if (result.campana() > 0 || result.mailsOk() > 0) {
                LOGGER.info("RF-15 aniversarios enviados: campana={}, mailsOk={}",
                        result.campana(), result.mailsOk());
            }
        } catch (RuntimeException exception) {
            LOGGER.error("RF-15: fallo al procesar aniversarios del día", exception);
        }
    }
}

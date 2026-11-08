package uy.ccisj.api.postulante;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class RetencionPermanenciaScheduler {
    private static final Logger LOGGER = LoggerFactory.getLogger(RetencionPermanenciaScheduler.class);

    private final RetencionPermanenciaService retencionPermanenciaService;

    public RetencionPermanenciaScheduler(RetencionPermanenciaService retencionPermanenciaService) {
        this.retencionPermanenciaService = retencionPermanenciaService;
    }

    /** Diario 08:15 hora Uruguay (después del job de aniversarios). */
    @Scheduled(cron = "0 15 8 * * *", zone = "America/Montevideo")
    public void procesarRetencionDiaria() {
        try {
            RetencionPermanenciaService.RetencionRunResult result = retencionPermanenciaService.procesar();
            if (result.avisos() > 0 || result.ocultados() > 0) {
                LOGGER.info("RF-13 retención: avisos={}, ocultados={}, mailsOk={}",
                        result.avisos(), result.ocultados(), result.mailsOk());
            }
        } catch (RuntimeException exception) {
            LOGGER.error("RF-13: fallo al procesar retención de permanencia", exception);
        }
    }
}

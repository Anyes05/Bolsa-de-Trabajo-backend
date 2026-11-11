package uy.ccisj.api.socio;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class SocioReentryScheduler {
    private static final Logger LOGGER = LoggerFactory.getLogger(SocioReentryScheduler.class);
    private final SocioService socios;
    private final SocioSubscriptionHistory history;

    public SocioReentryScheduler(SocioService socios, SocioSubscriptionHistory history) {
        this.socios = socios;
        this.history = history;
    }

    @Scheduled(initialDelay = 10000, fixedDelay = 60000)
    public void resumeDueSubscriptions() {
        for (Long id : history.dueSocios()) {
            try {
                socios.applyScheduledReentry(id);
            } catch (RuntimeException exception) {
                LOGGER.error("No se pudo reanudar suscripcion programada socioId={}; se reintentara", id, exception);
            }
        }
    }
}

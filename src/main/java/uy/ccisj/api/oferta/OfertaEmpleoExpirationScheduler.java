package uy.ccisj.api.oferta;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class OfertaEmpleoExpirationScheduler {
    private final OfertaEmpleoService ofertaService;

    public OfertaEmpleoExpirationScheduler(OfertaEmpleoService ofertaService) {
        this.ofertaService = ofertaService;
    }

    @Scheduled(cron = "0 0 * * * *", zone = "America/Montevideo")
    @Transactional
    public void expirePastOffers() {
        ofertaService.expirePastOffers();
    }
}

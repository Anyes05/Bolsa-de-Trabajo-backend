package uy.ccisj.api.socio;

import java.time.LocalDate;
import java.time.ZoneId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import uy.ccisj.api.socio.dto.FacturacionGeneradaDTO;
import uy.ccisj.api.socio.dto.GenerarFacturacionDTO;

@Component
public class FacturacionMensualScheduler {
    private static final Logger LOGGER = LoggerFactory.getLogger(FacturacionMensualScheduler.class);
    private final TarifaService tarifaService;

    public FacturacionMensualScheduler(TarifaService tarifaService) {
        this.tarifaService = tarifaService;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Scheduled(cron = "0 0 0 * * *", zone = "America/Montevideo")
    public void generarFacturacionDelMes() {
        LocalDate periodo = LocalDate.now(ZoneId.of("America/Montevideo")).withDayOfMonth(1);
        try {
            FacturacionGeneradaDTO resultado = tarifaService.generarFacturacion(new GenerarFacturacionDTO(periodo));
            if (resultado.cuotasGeneradas() > 0) {
                LOGGER.info("Facturacion automatica del periodo {}: {} cuotas nuevas",
                        periodo, resultado.cuotasGeneradas());
            }
        } catch (ResponseStatusException exception) {
            if (exception.getStatusCode().value() == HttpStatus.NOT_FOUND.value()) {
                LOGGER.warn("No se genero la facturacion de {}: falta configurar la tarifa anual", periodo);
            } else {
                LOGGER.error("No se pudo generar la facturacion automatica de {}", periodo, exception);
            }
        } catch (RuntimeException exception) {
            LOGGER.error("No se pudo generar la facturacion automatica de {}", periodo, exception);
        }
    }
}
package uy.ccisj.api.socio;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import uy.ccisj.api.socio.dto.FacturacionGeneradaDTO;
import uy.ccisj.api.socio.dto.GenerarFacturacionDTO;

/**
 * Los jobs programados nunca deben propagar excepciones: registran el error y
 * siguen funcionando en la próxima ejecución.
 */
class SocioSchedulersCoverageTest {

    // ------------------------------------------------------- facturación mensual

    private final TarifaService tarifas = mock(TarifaService.class);
    private final FacturacionMensualScheduler facturacion = new FacturacionMensualScheduler(tarifas);

    private LocalDate periodoEsperado() {
        return LocalDate.now(ZoneId.of("America/Montevideo")).withDayOfMonth(1).minusMonths(1);
    }

    @Test
    void facturaElMesAnteriorConCuotasNuevas() {
        when(tarifas.generarFacturacion(any())).thenReturn(
                new FacturacionGeneradaDTO(periodoEsperado(), 1L, 3, 2, 1));

        facturacion.generarFacturacionDelMes();

        ArgumentCaptor<GenerarFacturacionDTO> dto = ArgumentCaptor.forClass(GenerarFacturacionDTO.class);
        verify(tarifas).generarFacturacion(dto.capture());
        assertEquals(periodoEsperado(), dto.getValue().periodo());
    }

    @Test
    void facturacionSinCuotasNuevasNoFalla() {
        when(tarifas.generarFacturacion(any())).thenReturn(
                new FacturacionGeneradaDTO(periodoEsperado(), 1L, 3, 0, 3));

        assertDoesNotThrow(facturacion::generarFacturacionDelMes);
        verify(tarifas).generarFacturacion(any());
    }

    @Test
    void faltaDeTarifaUOtrosErroresHttpSeRegistranSinPropagar() {
        when(tarifas.generarFacturacion(any()))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "sin tarifa"))
                .thenThrow(new ResponseStatusException(HttpStatus.CONFLICT, "conflicto"));

        assertDoesNotThrow(facturacion::generarFacturacionDelMes);
        assertDoesNotThrow(facturacion::generarFacturacionDelMes);
        verify(tarifas, times(2)).generarFacturacion(any());
    }

    @Test
    void erroresInesperadosDeFacturacionNoSePropagan() {
        when(tarifas.generarFacturacion(any())).thenThrow(new IllegalStateException("BD caída"));

        assertDoesNotThrow(facturacion::generarFacturacionDelMes);
    }

    // ------------------------------------------------------- reingresos programados

    @Test
    void reanudaCadaSuscripcionVencidaAunqueUnaFalle() {
        SocioService socios = mock(SocioService.class);
        SocioSubscriptionHistory history = mock(SocioSubscriptionHistory.class);
        when(history.dueSocios()).thenReturn(List.of(1L, 2L, 3L));
        doThrow(new IllegalStateException("bloqueado")).when(socios).applyScheduledReentry(2L);

        assertDoesNotThrow(() -> new SocioReentryScheduler(socios, history).resumeDueSubscriptions());

        verify(socios).applyScheduledReentry(1L);
        verify(socios).applyScheduledReentry(2L);
        verify(socios).applyScheduledReentry(3L);
    }

    // ------------------------------------------------------- aniversarios

    @Test
    void aniversariosProcesaElDiaActualConYSinEnvios() {
        AniversarioService service = mock(AniversarioService.class);
        AniversarioScheduler scheduler = new AniversarioScheduler(service);
        LocalDate hoy = LocalDate.now();
        when(service.procesarAniversariosDelDia(null)).thenReturn(
                new AniversarioService.AniversarioRunResult(hoy, 2, 2, 1, 1, 0),
                new AniversarioService.AniversarioRunResult(hoy, 1, 0, 1, 0, 1),
                new AniversarioService.AniversarioRunResult(hoy, 0, 0, 0, 0, 0));

        scheduler.enviarAniversariosDelDia();
        scheduler.enviarAniversariosDelDia();
        scheduler.enviarAniversariosDelDia();

        verify(service, times(3)).procesarAniversariosDelDia(null);
    }

    @Test
    void falloDeAniversariosNoSePropaga() {
        AniversarioService service = mock(AniversarioService.class);
        when(service.procesarAniversariosDelDia(null)).thenThrow(new IllegalStateException("SMTP"));

        assertDoesNotThrow(() -> new AniversarioScheduler(service).enviarAniversariosDelDia());
    }
}

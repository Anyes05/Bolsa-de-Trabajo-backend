package uy.ccisj.api.socio;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;
import uy.ccisj.api.domain.EstadoMorosidad;
import uy.ccisj.api.socio.dto.FacturacionGeneradaDTO;
import uy.ccisj.api.socio.dto.GenerarFacturacionDTO;
import uy.ccisj.api.socio.dto.GuardarTarifaDTO;
import uy.ccisj.api.socio.dto.TarifaDTO;

/** Tarifas anuales y facturación mensual, incluido el arrastre del timbre recurrente (RF-02). */
class TarifaServiceCoverageTest {
    private final TarifaRepository tarifas = mock(TarifaRepository.class);
    private final CuotaRepository cuotas = mock(CuotaRepository.class);
    private final SocioRepository socios = mock(SocioRepository.class);
    private final CajaService caja = mock(CajaService.class);
    private final TarifaService service = new TarifaService(tarifas, cuotas, socios, caja);

    private static Tarifa tarifa(long id, int anio) {
        Tarifa tarifa = new Tarifa(anio, new BigDecimal("500"), 10);
        ReflectionTestUtils.setField(tarifa, "id", id);
        return tarifa;
    }

    private static Socio socio(long id, EstadoMorosidad estado) {
        Socio socio = new Socio(null, "Socio " + id);
        ReflectionTestUtils.setField(socio, "id", id);
        socio.setEstadoMorosidad(estado);
        return socio;
    }

    private static Cuota anterior(Socio socio, BigDecimal timbre, boolean recurrente) {
        Cuota cuota = new Cuota(socio, LocalDate.of(2026, 4, 1), BigDecimal.TEN, LocalDate.of(2026, 5, 31));
        cuota.setMontoTimbre(timbre);
        cuota.setTimbreRecurrente(recurrente);
        return cuota;
    }

    @Test
    void listarDevuelveTarifasConvertidas() {
        when(tarifas.findAllByOrderByAnioDesc()).thenReturn(List.of(tarifa(2L, 2027), tarifa(1L, 2026)));

        List<TarifaDTO> lista = service.listar();

        assertEquals(List.of(2027, 2026), lista.stream().map(TarifaDTO::anio).toList());
        assertEquals(2L, lista.getFirst().id());
        assertEquals(10, lista.getFirst().diaVencimiento());
    }

    @Test
    void guardarCreaActualizaORechazaTarifaConCuotas() {
        when(tarifas.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(tarifas.findByAnio(2030)).thenReturn(Optional.empty());
        TarifaDTO nueva = service.guardar(new GuardarTarifaDTO(2030, new BigDecimal("700"), 5));
        assertEquals(2030, nueva.anio());
        assertEquals(new BigDecimal("700"), nueva.montoBase());

        Tarifa existente = tarifa(3L, 2031);
        when(tarifas.findByAnio(2031)).thenReturn(Optional.of(existente));
        TarifaDTO actualizada = service.guardar(new GuardarTarifaDTO(2031, new BigDecimal("800"), 20));
        assertEquals(3L, actualizada.id());
        assertEquals(new BigDecimal("800"), existente.getMontoBase());
        assertEquals(20, existente.getDiaVencimiento());

        when(cuotas.existsByTarifaId(3L)).thenReturn(true);
        var error = assertThrows(ResponseStatusException.class,
                () -> service.guardar(new GuardarTarifaDTO(2031, BigDecimal.ONE, 1)));
        assertEquals(409, error.getStatusCode().value());
    }

    @Test
    void facturacionSoloArrastraTimbreRecurrentePositivo() {
        Tarifa tarifa = tarifa(1L, 2026);
        when(tarifas.findByAnio(2026)).thenReturn(Optional.of(tarifa));
        Socio conTimbre = socio(1L, EstadoMorosidad.AL_DIA);
        Socio timbreNulo = socio(2L, EstadoMorosidad.MOROSO);
        Socio timbreCero = socio(3L, EstadoMorosidad.AL_DIA);
        Socio noRecurrente = socio(4L, EstadoMorosidad.AL_DIA);
        Socio yaFacturado = socio(5L, EstadoMorosidad.AL_DIA);
        Socio inactivo = socio(6L, EstadoMorosidad.INACTIVO);
        when(socios.findAllByOrderByRazonSocialAsc()).thenReturn(
                List.of(conTimbre, timbreNulo, timbreCero, noRecurrente, yaFacturado, inactivo));
        LocalDate periodo = LocalDate.of(2026, 5, 1);
        when(cuotas.existsBySocioIdAndPeriodo(5L, periodo)).thenReturn(true);
        when(cuotas.findFirstBySocioIdOrderByPeriodoDesc(1L)).thenReturn(Optional.of(anterior(conTimbre, new BigDecimal("50"), true)));
        when(cuotas.findFirstBySocioIdOrderByPeriodoDesc(2L)).thenReturn(Optional.of(anterior(timbreNulo, null, true)));
        when(cuotas.findFirstBySocioIdOrderByPeriodoDesc(3L)).thenReturn(Optional.of(anterior(timbreCero, BigDecimal.ZERO, true)));
        when(cuotas.findFirstBySocioIdOrderByPeriodoDesc(4L)).thenReturn(Optional.of(anterior(noRecurrente, new BigDecimal("50"), false)));

        FacturacionGeneradaDTO resultado = service.generarFacturacion(new GenerarFacturacionDTO(LocalDate.of(2026, 5, 17)));

        assertEquals(new FacturacionGeneradaDTO(periodo, 1L, 5, 4, 1), resultado);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Cuota>> guardadas = ArgumentCaptor.forClass(List.class);
        verify(cuotas).saveAll(guardadas.capture());
        List<Cuota> nuevas = guardadas.getValue();
        assertEquals(List.of(1L, 2L, 3L, 4L), nuevas.stream().map(c -> c.getSocio().getId()).toList());
        assertEquals(new BigDecimal("50"), nuevas.get(0).getMontoTimbre());
        assertTrue(nuevas.get(0).isTimbreRecurrente());
        for (Cuota sinArrastre : nuevas.subList(1, 4)) {
            assertEquals(0, sinArrastre.getMontoTimbre().signum());
            assertFalse(sinArrastre.isTimbreRecurrente());
        }
        assertSame(tarifa, nuevas.getFirst().getTarifa());
        assertEquals(SaldoCuenta.vencimiento(periodo), nuevas.getFirst().getFechaVencimiento());
        verify(caja).iniciarRegistro("Facturación del período 2026-05-17");
        verify(caja, times(5)).generarReintegros(any(), eq(periodo));
        verify(caja, never()).generarReintegros(eq(inactivo), any());
    }

    @Test
    void facturacionSinTarifaDelAnioDevuelve404() {
        when(tarifas.findByAnio(2040)).thenReturn(Optional.empty());

        var error = assertThrows(ResponseStatusException.class,
                () -> service.generarFacturacion(new GenerarFacturacionDTO(LocalDate.of(2040, 1, 1))));

        assertEquals(404, error.getStatusCode().value());
        verify(cuotas, never()).saveAll(any());
    }
}

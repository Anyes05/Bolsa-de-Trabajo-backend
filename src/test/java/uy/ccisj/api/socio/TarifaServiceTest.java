package uy.ccisj.api.socio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import uy.ccisj.api.domain.EstadoMorosidad;
import uy.ccisj.api.socio.dto.GenerarFacturacionDTO;
import uy.ccisj.api.socio.dto.GuardarTarifaDTO;

class TarifaServiceTest {
    private final TarifaRepository tarifas = mock(TarifaRepository.class);
    private final CuotaRepository cuotas = mock(CuotaRepository.class);
    private final SocioRepository socios = mock(SocioRepository.class);
    private final CajaService caja = mock(CajaService.class);
    private TarifaService service;

    @BeforeEach
    void setUp() {
        service = new TarifaService(tarifas, cuotas, socios, caja);
    }

    @Test
    void listaYGuardaNuevaTarifa() {
        Tarifa existing = new Tarifa(2025, new BigDecimal("1200.00"), 10);
        when(tarifas.findAllByOrderByAnioDesc()).thenReturn(List.of(existing));
        when(tarifas.findByAnio(2026)).thenReturn(Optional.empty());
        when(tarifas.save(any(Tarifa.class))).thenAnswer(invocation -> invocation.getArgument(0));

        assertEquals(2025, service.listar().get(0).anio());
        var saved = service.guardar(new GuardarTarifaDTO(2026, new BigDecimal("1500.00"), 15));
        assertEquals(2026, saved.anio());
        assertEquals(new BigDecimal("1500.00"), saved.montoBase());
        assertEquals(15, saved.diaVencimiento());
    }

    @Test
    void actualizaTarifaSinCuotasYProtegeLaYaFacturada() {
        Tarifa tarifa = new Tarifa(2026, new BigDecimal("1000.00"), 10);
        when(tarifas.findByAnio(2026)).thenReturn(Optional.of(tarifa));
        when(tarifas.save(tarifa)).thenReturn(tarifa);

        var updated = service.guardar(new GuardarTarifaDTO(2026, new BigDecimal("1700.00"), 20));
        assertEquals(new BigDecimal("1700.00"), updated.montoBase());
        assertEquals(20, updated.diaVencimiento());

        when(cuotas.existsByTarifaId(tarifa.getId())).thenReturn(true);
        assertStatus(HttpStatus.CONFLICT, () -> service.guardar(
                new GuardarTarifaDTO(2026, new BigDecimal("1800.00"), 25)));
    }

    @Test
    void facturacionRequiereTarifaDelAnio() {
        LocalDate period = LocalDate.of(2026, 11, 21);

        assertStatus(HttpStatus.NOT_FOUND,
                () -> service.generarFacturacion(new GenerarFacturacionDTO(period)));
        verify(caja).iniciarRegistro("Facturación del período " + period);
    }

    @Test
    void facturaSoloSociosActivosSinCuotaPreviaYActualizaCaja() {
        LocalDate requested = LocalDate.of(2026, 11, 21);
        LocalDate period = LocalDate.of(2026, 11, 1);
        Tarifa tarifa = new Tarifa(2026, new BigDecimal("1500.00"), 10);
        Socio nuevo = socio(1L, EstadoMorosidad.AL_DIA);
        Socio existente = socio(2L, EstadoMorosidad.MOROSO);
        Socio inactivo = socio(3L, EstadoMorosidad.INACTIVO);
        Cuota anterior = mock(Cuota.class);
        when(anterior.isTimbreRecurrente()).thenReturn(true);
        when(anterior.getMontoTimbre()).thenReturn(new BigDecimal("250.00"));
        when(tarifas.findByAnio(2026)).thenReturn(Optional.of(tarifa));
        when(socios.findAllByOrderByRazonSocialAsc()).thenReturn(List.of(nuevo, existente, inactivo));
        when(cuotas.existsBySocioIdAndPeriodo(1L, period)).thenReturn(false);
        when(cuotas.existsBySocioIdAndPeriodo(2L, period)).thenReturn(true);
        when(cuotas.findFirstBySocioIdOrderByPeriodoDesc(1L)).thenReturn(Optional.of(anterior));

        var result = service.generarFacturacion(new GenerarFacturacionDTO(requested));

        assertEquals(period, result.periodo());
        assertEquals(2, result.sociosActivos());
        assertEquals(1, result.cuotasGeneradas());
        assertEquals(1, result.cuotasExistentes());
        verify(cuotas).saveAll(any());
        verify(cuotas).flush();
        verify(caja).generarReintegros(nuevo, period);
        verify(caja).generarReintegros(existente, period);
        verify(caja).actualizarMorosidad(nuevo);
        verify(caja).actualizarMorosidad(existente);
    }

    private Socio socio(Long id, EstadoMorosidad status) {
        Socio socio = mock(Socio.class);
        when(socio.getId()).thenReturn(id);
        when(socio.getEstadoMorosidad()).thenReturn(status);
        return socio;
    }

    private void assertStatus(HttpStatus expected, Runnable action) {
        ResponseStatusException error = assertThrows(ResponseStatusException.class, action::run);
        assertEquals(expected, error.getStatusCode());
    }
}

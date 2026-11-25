package uy.ccisj.api.socio;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.Query;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;
import uy.ccisj.api.catalogo.RubroEmpleo;
import uy.ccisj.api.common.ListQuery;
import uy.ccisj.api.domain.EstadoCuota;
import uy.ccisj.api.domain.EstadoMorosidad;
import uy.ccisj.api.domain.MetodoPago;
import uy.ccisj.api.socio.dto.CajaResumenDTO;
import uy.ccisj.api.socio.dto.CuentaCajaDTO;
import uy.ccisj.api.socio.dto.HistorialCajaDTO;
import uy.ccisj.api.socio.dto.MovimientoCajaDTO;
import uy.ccisj.api.socio.dto.RegistrarCobroDTO;
import uy.ccisj.api.socio.dto.RegistrarReintegroDTO;
import uy.ccisj.api.socio.dto.ReintegroProgramadoDTO;
import uy.ccisj.api.user.Role;
import uy.ccisj.api.user.User;

/**
 * Cobertura de CajaService con repositorios simulados: saldos, historial,
 * cobros, reintegros puntuales/recurrentes y reintegros programados.
 */
class CajaServiceCoverageTest {
    private final SocioRepository socios = mock(SocioRepository.class);
    private final CuotaRepository cuotas = mock(CuotaRepository.class);
    private final PagoRepository pagos = mock(PagoRepository.class);
    private final ReintegroRepository reintegros = mock(ReintegroRepository.class);
    private final ReintegroProgramadoRepository programados = mock(ReintegroProgramadoRepository.class);
    private final EntityManager em = mock(EntityManager.class);
    private final Query query = mock(Query.class);
    private final CajaService caja = new CajaService(socios, cuotas, pagos, reintegros, programados, em);

    private final LocalDate hoy = LocalDate.now();
    private final LocalDate mesActual = hoy.withDayOfMonth(1);

    @BeforeEach
    void setUp() {
        when(em.createNativeQuery(anyString())).thenReturn(query);
        when(query.setParameter(anyString(), any())).thenReturn(query);
    }

    // ----------------------------------------------------------------- helpers

    private static <T> T conId(T entidad, long id) {
        ReflectionTestUtils.setField(entidad, "id", id);
        return entidad;
    }

    private static User usuario(String bps, String cedula, boolean activo) {
        User user = new User("socio" + bps + cedula + "@example.test", bps, "hash", Role.SOCIO);
        user.setCedula(cedula);
        user.setActivo(activo);
        return user;
    }

    private static Socio socio(long id, User user, EstadoMorosidad estado) {
        Socio socio = conId(new Socio(user, "Empresa " + id), id);
        socio.setEstadoMorosidad(estado);
        socio.setTelefono("099" + id);
        socio.setRut("RUT" + id);
        return socio;
    }

    private static Cuota cuota(long id, Socio socio, LocalDate periodo, String monto, EstadoCuota estado) {
        Cuota cuota = conId(new Cuota(socio, periodo, new BigDecimal(monto), SaldoCuenta.vencimiento(periodo)), id);
        cuota.setEstado(estado);
        return cuota;
    }

    private void bloquear(Socio socio) {
        when(em.find(Socio.class, socio.getId(), LockModeType.PESSIMISTIC_WRITE)).thenReturn(socio);
    }

    // ----------------------------------------------------------------- resumen

    @Test
    void resumenCuentaAlDiaPendientesEInactivosConDatosDeCuota() {
        Socio alDia = socio(1L, usuario("BPS1", "111", true), EstadoMorosidad.AL_DIA);
        alDia.setRubro(new RubroEmpleo("Comercio"));
        Socio moroso = socio(2L, usuario(" ", "222", true), EstadoMorosidad.AL_DIA);
        Socio inactivo = socio(3L, null, EstadoMorosidad.INACTIVO);
        when(socios.findAllByOrderByRazonSocialAsc()).thenReturn(List.of(alDia, moroso, inactivo));

        // Socio 2: cuota vencida impaga, timbre tomado de la cuota (sin programados activos).
        Cuota vencida = cuota(20L, moroso, mesActual.minusMonths(3), "100", EstadoCuota.PENDIENTE);
        vencida.setMontoTimbre(new BigDecimal("15"));
        vencida.setTimbreRecurrente(true);
        when(cuotas.findBySocioIdOrderByPeriodoDesc(2L)).thenReturn(List.of(vencida));
        when(cuotas.findFirstBySocioIdOrderByPeriodoDesc(2L)).thenReturn(Optional.of(vencida));
        ReintegroProgramado pausado = conId(new ReintegroProgramado(moroso, "Pausado", BigDecimal.ONE, mesActual), 9L);
        pausado.setActivo(false);
        when(programados.findBySocioIdOrderByIdAsc(2L)).thenReturn(List.of(pausado));

        // Socio 1: programados activos suman el timbre aunque la cuota tenga otro monto.
        Cuota cuotaAlDia = cuota(10L, alDia, mesActual, "100", EstadoCuota.PAGADO);
        cuotaAlDia.setMontoTimbre(new BigDecimal("99"));
        when(cuotas.findFirstBySocioIdOrderByPeriodoDesc(1L)).thenReturn(Optional.of(cuotaAlDia));
        when(programados.findBySocioIdOrderByIdAsc(1L)).thenReturn(List.of(
                conId(new ReintegroProgramado(alDia, "Timbre", new BigDecimal("20"), mesActual), 1L),
                conId(new ReintegroProgramado(alDia, "Gasto", new BigDecimal("5"), mesActual), 2L)));

        CajaResumenDTO resumen = caja.getResumen();

        assertEquals(3, resumen.totalSocios());
        assertEquals(1, resumen.alDia());
        assertEquals(1, resumen.pendiente());
        assertEquals(1, resumen.inactivos());

        var cuentas = caja.listarCuentas(ListQuery.of(null, 0, 100), CajaService.FiltroCaja.TODOS).content();
        assertEquals(3, cuentas.size());
        // Filtros en servidor: coinciden con las métricas del resumen.
        assertEquals(resumen.alDia(), caja.listarCuentas(ListQuery.of(null, 0, 100), CajaService.FiltroCaja.AL_DIA).totalElements());
        assertEquals(resumen.pendiente(), caja.listarCuentas(ListQuery.of(null, 0, 100), CajaService.FiltroCaja.DEUDA).totalElements());
        assertEquals(resumen.inactivos(), caja.listarCuentas(ListQuery.of(null, 0, 100), CajaService.FiltroCaja.INACTIVO).totalElements());
        assertEquals(1, caja.listarCuentas(ListQuery.of(null, 0, 100), CajaService.FiltroCaja.MOROSO).totalElements());
        // Paginación: página de 1 elemento informa el total.
        var primera = caja.listarCuentas(ListQuery.of(null, 0, 1), null);
        assertEquals(1, primera.content().size());
        assertEquals(3, primera.totalElements());
        assertEquals(3, primera.totalPages());
        // Búsqueda por rubro sin distinguir mayúsculas ni tildes.
        assertEquals(1, caja.listarCuentas(ListQuery.of("COMERCIO", 0, 100), CajaService.FiltroCaja.TODOS).totalElements());
        // Exportación: solo los estados pedidos; sin estados no exporta nada.
        assertEquals(1, caja.cuentasParaExportar(ListQuery.of(null, null, null), java.util.Set.of(EstadoMorosidad.MOROSO)).size());
        assertTrue(caja.cuentasParaExportar(ListQuery.of(null, null, null), java.util.Set.of()).isEmpty());

        CuentaCajaDTO cuenta1 = cuentas.get(0);
        assertEquals("BPS1", cuenta1.bps());
        assertEquals("111", cuenta1.cedula());
        assertEquals("Comercio", cuenta1.nombreRubro());
        assertEquals(0, new BigDecimal("25").compareTo(cuenta1.montoTimbre()));
        assertTrue(cuenta1.timbreRecurrente());
        assertEquals(10L, cuenta1.cuotaId());
        assertEquals(EstadoCuota.PAGADO, cuenta1.estadoCuota());
        assertEquals(mesActual, cuenta1.periodo());

        CuentaCajaDTO cuenta2 = cuentas.get(1);
        assertEquals("222", cuenta2.bps(), "BPS en blanco usa la cédula como identificador");
        assertEquals(EstadoMorosidad.MOROSO, cuenta2.estadoMorosidad());
        assertEquals(0, new BigDecimal("15").compareTo(cuenta2.montoTimbre()));
        assertTrue(cuenta2.timbreRecurrente());
        assertEquals(0, new BigDecimal("100").compareTo(cuenta2.deudaVencida()));
        assertNull(cuenta2.nombreRubro());

        CuentaCajaDTO cuenta3 = cuentas.get(2);
        assertNull(cuenta3.bps());
        assertNull(cuenta3.cedula());
        assertNull(cuenta3.cuotaId());
        assertNull(cuenta3.estadoCuota());
        assertNull(cuenta3.montoCuota());
        assertNull(cuenta3.periodo());
        assertNull(cuenta3.fechaVencimiento());
        assertFalse(cuenta3.timbreRecurrente());
        assertEquals(0, cuenta3.montoTimbre().signum());
        assertEquals(EstadoMorosidad.INACTIVO, cuenta3.estadoMorosidad());
    }

    @Test
    void cuentaConCuotaSinTimbreNoRecurrenteQuedaEnCero() {
        Socio socio = socio(4L, usuario(null, "444", true), EstadoMorosidad.AL_DIA);
        Cuota cuota = cuota(40L, socio, mesActual, "100", EstadoCuota.PENDIENTE);
        cuota.setMontoTimbre(null);
        when(socios.findAllByOrderByRazonSocialAsc()).thenReturn(List.of(socio));
        when(cuotas.findFirstBySocioIdOrderByPeriodoDesc(4L)).thenReturn(Optional.of(cuota));

        CuentaCajaDTO cuenta = caja.listarCuentas(ListQuery.of(null, null, null), null).content().getFirst();

        assertEquals("444", cuenta.bps(), "sin BPS se informa la cédula");
        assertEquals(0, cuenta.montoTimbre().signum());
        assertFalse(cuenta.timbreRecurrente());
    }

    // ----------------------------------------------------------------- historial

    @Test
    void historialOrdenaMovimientosYCalculaSaldoAFavorUtilizado() {
        User user = usuario("BPS5", "555", true);
        Socio socio = socio(5L, user, EstadoMorosidad.AL_DIA);
        socio.setRubro(new RubroEmpleo("Industria"));
        when(socios.findById(5L)).thenReturn(Optional.of(socio));

        LocalDate periodo = mesActual.minusMonths(1);
        Cuota cuotaVigente = cuota(50L, socio, periodo, "100", EstadoCuota.PENDIENTE);
        when(cuotas.findBySocioIdOrderByPeriodoDesc(5L)).thenReturn(List.of(cuotaVigente));

        Reintegro sinCuota = conId(new Reintegro(socio, "Timbre", new BigDecimal("30"), periodo,
                SaldoCuenta.vencimiento(periodo), "recurrente:1:" + periodo, "recurrente"), 60L);
        Reintegro deCuotaVigente = conId(new Reintegro(socio, "Gasto", new BigDecimal("10"), periodo,
                SaldoCuenta.vencimiento(periodo), null, null), 61L);
        ReflectionTestUtils.setField(deCuotaVigente, "cuota", cuotaVigente);
        when(reintegros.findBySocioIdOrderByFechaAscIdAsc(5L)).thenReturn(List.of(sinCuota, deCuotaVigente));

        Pago anticipo = conId(new Pago(socio, new BigDecimal("50"), periodo.minusDays(5), MetodoPago.EFECTIVO), 70L);
        anticipo.setNroCobranzaExterno("REF-1");
        anticipo.setObservaciones("Anticipo");
        Pago mismoDia = conId(new Pago(cuotaVigente, new BigDecimal("40"), periodo, MetodoPago.EFECTIVO), 71L);
        when(pagos.findBySocioIdOrderByFechaEmisionAscIdAsc(5L)).thenReturn(List.of(anticipo, mismoDia));

        ReintegroProgramado programado = conId(new ReintegroProgramado(socio, "Timbre", new BigDecimal("30"), periodo), 80L);
        when(programados.findBySocioIdOrderByIdAsc(5L)).thenReturn(List.of(programado));

        HistorialCajaDTO historial = caja.getHistorial(5L);

        assertEquals(5L, historial.socioId());
        assertEquals("Empresa 5", historial.razonSocial());
        assertEquals("BPS5", historial.bps());
        assertEquals("555", historial.cedula());
        assertEquals("RUT5", historial.rut());
        assertEquals("Industria", historial.nombreRubro());
        // Cargos: 100 + 30 + 10 = 140; pagos: 50 + 40 = 90.
        assertEquals(0, new BigDecimal("50").compareTo(historial.saldo()));
        assertEquals(List.of("PAGO", "CUOTA", "REINTEGRO", "REINTEGRO", "PAGO"),
                historial.movimientos().stream().map(MovimientoCajaDTO::tipo).toList());

        MovimientoCajaDTO pagoInicial = historial.movimientos().get(0);
        assertEquals(0, new BigDecimal("-50").compareTo(pagoInicial.saldoResultante()));
        assertEquals(0, pagoInicial.saldoFavorUtilizado().signum());
        assertEquals("REF-1", pagoInicial.referencia());
        assertEquals("Anticipo", pagoInicial.observaciones());
        assertEquals(MetodoPago.EFECTIVO, pagoInicial.metodoPago());

        MovimientoCajaDTO cuotaMov = historial.movimientos().get(1);
        assertEquals("Cuota " + java.time.YearMonth.from(periodo), cuotaMov.concepto());
        assertEquals(0, new BigDecimal("-50").compareTo(cuotaMov.saldoAnterior()));
        assertEquals(0, new BigDecimal("50").compareTo(cuotaMov.saldoFavorUtilizado()));
        assertEquals(0, new BigDecimal("50").compareTo(cuotaMov.saldoResultante()));
        assertEquals(0, BigDecimal.TEN.compareTo(cuotaMov.pendiente()), "los 90 cobrados se imputan primero a la cuota");

        MovimientoCajaDTO timbre = historial.movimientos().get(2);
        assertTrue(timbre.recurrente());
        assertEquals(0, timbre.saldoFavorUtilizado().signum());
        MovimientoCajaDTO gasto = historial.movimientos().get(3);
        assertFalse(gasto.recurrente());
        assertEquals(0, new BigDecimal("10").compareTo(gasto.pendiente()));
        assertEquals(0, new BigDecimal("90").compareTo(gasto.saldoResultante()));
        assertEquals(0, new BigDecimal("50").compareTo(historial.movimientos().get(4).saldoResultante()));

        assertEquals(List.of(new ReintegroProgramadoDTO(80L, "Timbre", new BigDecimal("30"), true)),
                historial.reintegrosProgramados());
    }

    @Test
    void historialDeSocioSinRubroNiUsuarioQuedaVacio() {
        Socio socio = socio(23L, null, EstadoMorosidad.AL_DIA);
        when(socios.findById(23L)).thenReturn(Optional.of(socio));

        HistorialCajaDTO historial = caja.getHistorial(23L);

        assertNull(historial.nombreRubro());
        assertNull(historial.bps());
        assertNull(historial.cedula());
        assertTrue(historial.movimientos().isEmpty());
        assertTrue(historial.reintegrosProgramados().isEmpty());
        assertEquals(0, historial.saldo().signum());
        assertEquals(EstadoMorosidad.AL_DIA, historial.estadoMorosidad());
    }

    @Test
    void historialDeSocioInexistenteDevuelve404() {
        when(socios.findById(99L)).thenReturn(Optional.empty());

        var error = assertThrows(ResponseStatusException.class, () -> caja.getHistorial(99L));

        assertEquals(404, error.getStatusCode().value());
    }

    // ----------------------------------------------------------------- cobros

    @Test
    void registrarCobroGuardaPagoConReferenciasNormalizadasYActualizaMorosidad() {
        Socio socio = socio(6L, usuario("BPS6", "666", true), EstadoMorosidad.MOROSO);
        bloquear(socio);
        Cuota cuota = cuota(600L, socio, mesActual.minusMonths(3), "100", EstadoCuota.PENDIENTE);
        when(cuotas.findBySocioIdOrderByPeriodoDesc(6L)).thenReturn(List.of(cuota));
        // Antes del cobro no hay pagos; luego se lee el pago recién guardado.
        Pago guardado = conId(new Pago(socio, new BigDecimal("100"), hoy, MetodoPago.EFECTIVO), 1L);
        when(pagos.findBySocioIdOrderByFechaEmisionAscIdAsc(6L)).thenReturn(List.of(), List.of(guardado));
        ArgumentCaptor<Pago> pago = ArgumentCaptor.forClass(Pago.class);

        CuentaCajaDTO cuenta = caja.registrarCobro(new RegistrarCobroDTO(6L, MetodoPago.EFECTIVO,
                new BigDecimal("100"), "  NRO-9  ", "   "));

        verify(pagos).saveAndFlush(pago.capture());
        assertEquals("NRO-9", pago.getValue().getNroCobranzaExterno());
        assertNull(pago.getValue().getObservaciones());
        assertEquals(hoy, pago.getValue().getFechaEmision());
        assertEquals(EstadoCuota.PAGADO, cuota.getEstado());
        assertEquals(EstadoMorosidad.AL_DIA, socio.getEstadoMorosidad());
        assertEquals(EstadoMorosidad.AL_DIA, cuenta.estadoMorosidad());
        assertEquals(0, cuenta.saldo().signum());
        verify(query).getSingleResult();
    }

    @Test
    void registrarCobroSinReferenciaNiObservacionesYSocioInactivoConDeuda() {
        Socio socio = socio(7L, usuario("BPS7", "777", false), EstadoMorosidad.INACTIVO);
        bloquear(socio);
        when(cuotas.findBySocioIdOrderByPeriodoDesc(7L))
                .thenReturn(List.of(cuota(700L, socio, mesActual, "100", EstadoCuota.PENDIENTE)));
        ArgumentCaptor<Pago> pago = ArgumentCaptor.forClass(Pago.class);

        CuentaCajaDTO cuenta = caja.registrarCobro(new RegistrarCobroDTO(7L, MetodoPago.EFECTIVO,
                new BigDecimal("40"), null, " Nota "));

        verify(pagos).saveAndFlush(pago.capture());
        assertNull(pago.getValue().getNroCobranzaExterno());
        assertEquals("Nota", pago.getValue().getObservaciones());
        assertEquals(EstadoMorosidad.INACTIVO, cuenta.estadoMorosidad());
    }

    @Test
    void registrarCobroRechazaInactivoSinDeudaYSocioInexistente() {
        Socio socio = socio(8L, usuario("BPS8", "888", true), EstadoMorosidad.INACTIVO);
        bloquear(socio);

        var conflicto = assertThrows(ResponseStatusException.class, () -> caja.registrarCobro(
                new RegistrarCobroDTO(8L, MetodoPago.EFECTIVO, BigDecimal.TEN, null, null)));
        var inexistente = assertThrows(ResponseStatusException.class, () -> caja.registrarCobro(
                new RegistrarCobroDTO(404L, MetodoPago.EFECTIVO, BigDecimal.TEN, null, null)));

        assertEquals(409, conflicto.getStatusCode().value());
        assertEquals(404, inexistente.getStatusCode().value());
        verify(pagos, never()).saveAndFlush(any());
    }

    // ----------------------------------------------------------------- reintegros

    @Test
    void reintegroPuntualNoTocaLaCuotaVigente() {
        Socio socio = socio(9L, usuario("BPS9", "999", true), EstadoMorosidad.AL_DIA);
        bloquear(socio);
        Cuota vigente = cuota(900L, socio, mesActual, "100", EstadoCuota.PENDIENTE);
        when(cuotas.findFirstBySocioIdOrderByPeriodoDesc(9L)).thenReturn(Optional.of(vigente));
        ArgumentCaptor<Reintegro> cargo = ArgumentCaptor.forClass(Reintegro.class);

        caja.registrarReintegro(9L, new RegistrarReintegroDTO("  Gasto puntual ", new BigDecimal("12"), false));

        verify(reintegros).saveAndFlush(cargo.capture());
        assertEquals("Gasto puntual", cargo.getValue().getConcepto());
        assertEquals("PUNTUAL", cargo.getValue().getTipo());
        assertNull(cargo.getValue().getOrigen());
        assertEquals(SaldoCuenta.vencimiento(hoy), cargo.getValue().getFechaVencimiento());
        verify(programados, never()).saveAndFlush(any());
        assertEquals(0, vigente.getMontoTimbre().signum());
        assertFalse(vigente.isTimbreRecurrente());
    }

    @Test
    void reintegroRecurrenteCreaProgramadoYMarcaTimbreEnCuotaVigente() {
        Socio socio = socio(10L, usuario("BPS10", "1010", true), EstadoMorosidad.AL_DIA);
        bloquear(socio);
        Cuota vigente = cuota(1000L, socio, mesActual, "100", EstadoCuota.PENDIENTE);
        when(cuotas.findFirstBySocioIdOrderByPeriodoDesc(10L)).thenReturn(Optional.of(vigente));
        when(programados.saveAndFlush(any())).thenAnswer(invocation -> conId(invocation.getArgument(0), 77L));
        ArgumentCaptor<Reintegro> cargo = ArgumentCaptor.forClass(Reintegro.class);

        CuentaCajaDTO cuenta = caja.registrarReintegro(10L,
                new RegistrarReintegroDTO("Timbre", new BigDecimal("25"), true));

        verify(reintegros).saveAndFlush(cargo.capture());
        assertEquals("RECURRENTE", cargo.getValue().getTipo());
        assertTrue(cargo.getValue().isRecurrente());
        assertEquals("recurrente:77:" + mesActual, cargo.getValue().getOrigen());
        assertEquals(0, new BigDecimal("25").compareTo(vigente.getMontoTimbre()));
        assertTrue(vigente.isTimbreRecurrente());
        assertTrue(cuenta.timbreRecurrente());
    }

    @Test
    void reintegroRecurrenteNoFallaSinCuota() {
        when(programados.saveAndFlush(any())).thenAnswer(invocation -> conId(invocation.getArgument(0), 78L));
        Socio sinCuota = socio(12L, usuario("BPS12", "1212", true), EstadoMorosidad.AL_DIA);
        bloquear(sinCuota);
        assertDoesNotThrow(() -> caja.registrarReintegro(12L,
                new RegistrarReintegroDTO("Timbre", new BigDecimal("25"), true)));
    }

    @Test
    void reintegroASocioInactivoEsConflicto() {
        Socio socio = socio(13L, usuario("BPS13", "1313", true), EstadoMorosidad.INACTIVO);
        bloquear(socio);

        var error = assertThrows(ResponseStatusException.class, () -> caja.registrarReintegro(13L,
                new RegistrarReintegroDTO("Gasto", BigDecimal.ONE, false)));

        assertEquals(409, error.getStatusCode().value());
        verify(reintegros, never()).saveAndFlush(any());
    }

    // ----------------------------------------------------------------- programados

    @Test
    void eliminarProgramadoConservaElCargoDelMesYLoBorra() {
        Socio socio = socio(14L, usuario("BPS14", "1414", true), EstadoMorosidad.AL_DIA);
        bloquear(socio);
        ReintegroProgramado programado = conId(new ReintegroProgramado(socio, "Timbre", new BigDecimal("30"),
                mesActual.minusMonths(2)), 140L);
        when(programados.findById(140L)).thenReturn(Optional.of(programado));
        ArgumentCaptor<Reintegro> cargo = ArgumentCaptor.forClass(Reintegro.class);

        caja.eliminarProgramado(14L, 140L);

        verify(reintegros).save(cargo.capture());
        assertEquals("recurrente:140:" + mesActual, cargo.getValue().getOrigen());
        assertEquals(mesActual, cargo.getValue().getFecha());
        assertEquals("RECURRENTE", cargo.getValue().getTipo());
        verify(reintegros).flush();
        verify(programados).delete(programado);
    }

    @Test
    void eliminarProgramadoDeSocioInactivoNoGeneraCargo() {
        Socio socio = socio(15L, usuario("BPS15", "1515", true), EstadoMorosidad.INACTIVO);
        bloquear(socio);
        ReintegroProgramado programado = conId(new ReintegroProgramado(socio, "Timbre", BigDecimal.TEN, mesActual), 150L);
        when(programados.findById(150L)).thenReturn(Optional.of(programado));

        caja.eliminarProgramado(15L, 150L);

        verify(reintegros, never()).save(any());
        verify(programados).delete(programado);
    }

    @Test
    void programadoDeOtroSocioOInexistenteDevuelve404() {
        Socio socio = socio(16L, usuario("BPS16", "1616", true), EstadoMorosidad.AL_DIA);
        Socio otro = socio(17L, usuario("BPS17", "1717", true), EstadoMorosidad.AL_DIA);
        bloquear(socio);
        when(programados.findById(170L)).thenReturn(Optional.of(
                conId(new ReintegroProgramado(otro, "Ajeno", BigDecimal.TEN, mesActual), 170L)));

        var ajeno = assertThrows(ResponseStatusException.class, () -> caja.eliminarProgramado(16L, 170L));
        var inexistente = assertThrows(ResponseStatusException.class,
                () -> caja.actualizarProgramado(16L, 171L, new ReintegroProgramadoDTO(171L, "X", BigDecimal.ONE, true)));

        assertEquals(404, ajeno.getStatusCode().value());
        assertEquals(404, inexistente.getStatusCode().value());
        verify(programados, never()).delete(any());
    }

    @Test
    void reactivarProgramadoPausadoLoDifiereAlMesSiguiente() {
        Socio socio = socio(18L, usuario("BPS18", "1818", true), EstadoMorosidad.AL_DIA);
        bloquear(socio);
        ReintegroProgramado programado = conId(new ReintegroProgramado(socio, "Timbre", BigDecimal.TEN,
                mesActual.minusMonths(5)), 180L);
        programado.setActivo(false);
        when(programados.findById(180L)).thenReturn(Optional.of(programado));

        ReintegroProgramadoDTO dto = caja.actualizarProgramado(18L, 180L,
                new ReintegroProgramadoDTO(180L, "  Timbre nuevo ", new BigDecimal("40"), true));

        assertEquals(new ReintegroProgramadoDTO(180L, "Timbre nuevo", new BigDecimal("40"), true), dto);
        assertEquals(mesActual.plusMonths(1), programado.getDesdePeriodo());
        verify(reintegros, never()).save(any());
    }

    @Test
    void pausarProgramadoActivoConservaCargoSiNoExistiaYMantieneDesde() {
        Socio socio = socio(19L, usuario("BPS19", "1919", true), EstadoMorosidad.AL_DIA);
        bloquear(socio);
        LocalDate desde = mesActual.minusMonths(1);
        ReintegroProgramado programado = conId(new ReintegroProgramado(socio, "Timbre", BigDecimal.TEN, desde), 190L);
        when(programados.findById(190L)).thenReturn(Optional.of(programado));
        when(reintegros.existsByOrigen("recurrente:190:" + mesActual)).thenReturn(true);

        ReintegroProgramadoDTO dto = caja.actualizarProgramado(19L, 190L,
                new ReintegroProgramadoDTO(190L, "Timbre", BigDecimal.ONE, false));

        assertFalse(dto.activo());
        assertFalse(programado.isActivo());
        assertEquals(desde, programado.getDesdePeriodo());
        verify(reintegros, never()).save(any(Reintegro.class));
    }

    @Test
    void activarProgramadoEnSocioInactivoEsConflictoPeroPausarloNo() {
        Socio socio = socio(20L, usuario("BPS20", "2020", true), EstadoMorosidad.INACTIVO);
        bloquear(socio);
        ReintegroProgramado programado = conId(new ReintegroProgramado(socio, "Timbre", BigDecimal.TEN, mesActual), 200L);
        when(programados.findById(200L)).thenReturn(Optional.of(programado));

        var error = assertThrows(ResponseStatusException.class, () -> caja.actualizarProgramado(20L, 200L,
                new ReintegroProgramadoDTO(200L, "Timbre", BigDecimal.ONE, true)));
        ReintegroProgramadoDTO pausado = caja.actualizarProgramado(20L, 200L,
                new ReintegroProgramadoDTO(200L, "Timbre", BigDecimal.ONE, false));

        assertEquals(409, error.getStatusCode().value());
        assertFalse(pausado.activo());
    }

    @Test
    void editarProgramadoPausadoSinReactivarloNoCambiaSuVigencia() {
        Socio socio = socio(24L, usuario("BPS24", "2424", true), EstadoMorosidad.AL_DIA);
        bloquear(socio);
        LocalDate desde = mesActual.minusMonths(3);
        ReintegroProgramado programado = conId(new ReintegroProgramado(socio, "Timbre", BigDecimal.TEN, desde), 240L);
        programado.setActivo(false);
        when(programados.findById(240L)).thenReturn(Optional.of(programado));

        ReintegroProgramadoDTO dto = caja.actualizarProgramado(24L, 240L,
                new ReintegroProgramadoDTO(240L, "Timbre corregido", new BigDecimal("12"), false));

        assertEquals(desde, programado.getDesdePeriodo());
        assertEquals("Timbre corregido", programado.getConcepto());
        assertEquals(0, new BigDecimal("12").compareTo(programado.getMonto()));
        assertFalse(dto.activo());
        verify(reintegros, never()).save(any());
    }

    @Test
    void generarReintegrosSoloEmiteProgramadosActivosVigentesYNoDuplicados() {
        Socio socio = socio(21L, usuario("BPS21", "2121", true), EstadoMorosidad.AL_DIA);
        bloquear(socio);
        LocalDate periodo = mesActual;
        ReintegroProgramado vigente = conId(new ReintegroProgramado(socio, "Vigente", BigDecimal.TEN, periodo), 1L);
        ReintegroProgramado futuro = conId(new ReintegroProgramado(socio, "Futuro", BigDecimal.TEN, periodo.plusMonths(1)), 2L);
        ReintegroProgramado pausado = conId(new ReintegroProgramado(socio, "Pausado", BigDecimal.TEN, periodo), 3L);
        pausado.setActivo(false);
        ReintegroProgramado yaEmitido = conId(new ReintegroProgramado(socio, "Emitido", BigDecimal.TEN, periodo), 4L);
        when(programados.findBySocioIdOrderByIdAsc(21L)).thenReturn(List.of(vigente, futuro, pausado, yaEmitido));
        when(reintegros.existsByOrigen("recurrente:4:" + periodo)).thenReturn(true);
        ArgumentCaptor<Reintegro> cargo = ArgumentCaptor.forClass(Reintegro.class);

        caja.generarReintegros(socio, periodo);

        verify(reintegros, times(1)).save(cargo.capture());
        assertEquals("Vigente", cargo.getValue().getConcepto());
        assertEquals(SaldoCuenta.vencimiento(periodo), cargo.getValue().getFechaVencimiento());
        verify(reintegros).flush();
    }

    @Test
    void generarReintegrosDeSocioInexistenteDevuelve404() {
        Socio fantasma = socio(22L, null, EstadoMorosidad.AL_DIA);

        var error = assertThrows(ResponseStatusException.class, () -> caja.generarReintegros(fantasma, mesActual));

        assertEquals(404, error.getStatusCode().value());
    }

    @Test
    void iniciarRegistroDejaAutorYMotivoEnLaSesionDeBaseDeDatos() {
        caja.iniciarRegistro("Motivo de prueba");

        verify(query).setParameter("autor", "SISTEMA:APLICACION");
        verify(query).setParameter("motivo", "Motivo de prueba");
        verify(query).getSingleResult();
    }
}

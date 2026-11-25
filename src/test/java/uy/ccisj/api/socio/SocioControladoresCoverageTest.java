package uy.ccisj.api.socio;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import uy.ccisj.api.domain.MetodoPago;
import uy.ccisj.api.socio.dto.CajaResumenDTO;
import uy.ccisj.api.socio.dto.CredencialesEnviadasDTO;
import uy.ccisj.api.socio.dto.CuentaCajaDTO;
import uy.ccisj.api.socio.dto.DirectorioSocioDTO;
import uy.ccisj.api.socio.dto.FacturacionGeneradaDTO;
import uy.ccisj.api.socio.dto.GenerarFacturacionDTO;
import uy.ccisj.api.socio.dto.GuardarTarifaDTO;
import uy.ccisj.api.socio.dto.HistorialCajaDTO;
import uy.ccisj.api.socio.dto.RegistrarCobroDTO;
import uy.ccisj.api.socio.dto.RegistrarReintegroDTO;
import uy.ccisj.api.socio.dto.ReintegroProgramadoDTO;
import uy.ccisj.api.socio.dto.RubroOptionDTO;
import uy.ccisj.api.socio.dto.SocioCorreosPreferenciaDTO;
import uy.ccisj.api.socio.dto.SocioCreateDTO;
import uy.ccisj.api.socio.dto.SocioPerfilUpdateDTO;
import uy.ccisj.api.socio.dto.SocioResponseDTO;
import uy.ccisj.api.socio.dto.SocioUpdateDTO;
import uy.ccisj.api.socio.dto.TarifaDTO;

/**
 * Los controladores del módulo socio solo delegan: se verifica que cada endpoint
 * pase los parámetros correctos al servicio y devuelva su respuesta sin cambios.
 */
class SocioControladoresCoverageTest {

    @Test
    void cajaControllerDelegaCadaOperacionEnElServicio() {
        CajaService service = mock(CajaService.class);
        CajaController controller = new CajaController(service);
        CajaResumenDTO resumen = new CajaResumenDTO(0, 0, 0, 0);
        var paginaCuentas = new uy.ccisj.api.common.PageResponse<CuentaCajaDTO>(List.of(), 1, 5, 0, 0);
        when(service.listarCuentas(new uy.ccisj.api.common.ListQuery("acme", 1, 5), CajaService.FiltroCaja.DEUDA))
                .thenReturn(paginaCuentas);
        assertSame(paginaCuentas, controller.cuentas(" acme ", CajaService.FiltroCaja.DEUDA, 1, 5));
        assertEquals(400, org.junit.jupiter.api.Assertions.assertThrows(
                org.springframework.web.server.ResponseStatusException.class,
                () -> controller.cuentas(null, null, 0, 1000)).getStatusCode().value(),
                "el tamaño de página está acotado");
        HistorialCajaDTO historial = mock(HistorialCajaDTO.class);
        CuentaCajaDTO cuenta = mock(CuentaCajaDTO.class);
        var cobro = new RegistrarCobroDTO(1L, MetodoPago.EFECTIVO, BigDecimal.TEN, null, null);
        var reintegro = new RegistrarReintegroDTO("Timbre", BigDecimal.ONE, true);
        var programado = new ReintegroProgramadoDTO(3L, "Timbre", BigDecimal.ONE, false);
        when(service.getResumen()).thenReturn(resumen);
        when(service.getHistorial(1L)).thenReturn(historial);
        when(service.registrarCobro(cobro)).thenReturn(cuenta);
        when(service.registrarReintegro(1L, reintegro)).thenReturn(cuenta);
        when(service.actualizarProgramado(1L, 3L, programado)).thenReturn(programado);

        assertSame(resumen, controller.resumen());
        assertSame(historial, controller.historial(1L));
        assertSame(cuenta, controller.registrarCobro(cobro));
        assertSame(cuenta, controller.registrarReintegro(1L, reintegro));
        assertSame(programado, controller.actualizarProgramado(1L, 3L, programado));
        controller.eliminarProgramado(1L, 3L);
        verify(service).eliminarProgramado(1L, 3L);
    }

    @Test
    void socioControllerDelegaAbmCredencialesYReingresos() {
        SocioService service = mock(SocioService.class);
        SocioSubscriptionHistory history = mock(SocioSubscriptionHistory.class);
        SocioController controller = new SocioController(service, history);
        SocioResponseDTO socio = mock(SocioResponseDTO.class);
        SocioCreateDTO create = new SocioCreateDTO(uy.ccisj.api.socio.dto.TipoIdentificadorSocio.BPS, "123",
                "socio@example.test", "Empresa", "211111110011", "099000000", null, "Calle", "1", "San José",
                1L, false, LocalDate.of(2020, 1, 1), LocalDate.of(1990, 5, 5));
        SocioUpdateDTO update = mock(SocioUpdateDTO.class);
        List<RubroOptionDTO> rubros = List.of(new RubroOptionDTO(1L, "Comercio"));
        var entradas = List.of(new SocioSubscriptionHistory.Entry(1L, "ALTA", LocalDate.of(2020, 1, 1), null, null, null));
        var credenciales = new CredencialesEnviadasDTO("a@example.test", "clave", "ok");
        LocalDate fecha = LocalDate.of(2026, 12, 1);
        when(service.getAllSocios()).thenReturn(List.of(socio));
        when(service.listRubros()).thenReturn(rubros);
        when(service.getSocioById(5L)).thenReturn(socio);
        when(service.createSocio(create)).thenReturn(socio);
        when(service.updateSocio(5L, update)).thenReturn(socio);
        when(service.deactivateSocio(5L)).thenReturn(socio);
        when(service.activateSocio(5L, fecha)).thenReturn(socio);
        when(history.list(5L)).thenReturn(entradas);
        when(service.resetClaveProvisional(5L)).thenReturn(socio);
        when(service.enviarCredenciales(5L)).thenReturn(credenciales);
        when(service.habilitarAcceso(5L)).thenReturn(socio);

        assertEquals(List.of(socio), controller.list());
        assertSame(rubros, controller.rubros());
        assertSame(socio, controller.get(5L));
        assertSame(socio, controller.create(create));
        assertSame(socio, controller.update(5L, update));
        assertSame(socio, controller.deactivate(5L));
        assertSame(socio, controller.activate(5L, new SocioController.ReentryRequest(fecha)));
        assertSame(entradas, controller.history(5L));
        controller.cancelReentry(5L);
        assertSame(socio, controller.resetClaveProvisional(5L));
        assertSame(credenciales, controller.enviarCredenciales(5L));
        assertSame(socio, controller.habilitarAcceso(5L));

        // El historial primero valida que el socio exista.
        verify(service, times(2)).getSocioById(5L);
        verify(service).cancelScheduledReentry(5L);
    }

    @Test
    void perfilYDirectorioUsanElEmailAutenticado() {
        SocioService service = mock(SocioService.class);
        Authentication auth = mock(Authentication.class);
        when(auth.getName()).thenReturn("socio@example.test");
        SocioResponseDTO perfil = mock(SocioResponseDTO.class);
        SocioPerfilUpdateDTO update = new SocioPerfilUpdateDTO("Empresa", "099000000", null, "Calle", "1",
                "San José", 1L, LocalDate.of(1990, 5, 5));
        List<DirectorioSocioDTO> directorio = List.of(new DirectorioSocioDTO(1L, "Empresa", "Comercio",
                "099000000", "socio@example.test", uy.ccisj.api.domain.EstadoMorosidad.AL_DIA,
                LocalDate.of(2020, 1, 1), true));
        List<RubroOptionDTO> rubros = List.of(new RubroOptionDTO(2L, "Servicios"));
        when(service.getMiPerfil("socio@example.test")).thenReturn(perfil);
        when(service.updateMiPerfil("socio@example.test", update)).thenReturn(perfil);
        when(service.updateSilenciarCorreos("socio@example.test", true)).thenReturn(perfil);
        when(service.listDirectorio("socio@example.test")).thenReturn(directorio);
        when(service.listRubros()).thenReturn(rubros);

        SocioPerfilController perfilController = new SocioPerfilController(service);
        assertSame(perfil, perfilController.get(auth));
        assertSame(perfil, perfilController.update(auth, update));
        assertSame(perfil, perfilController.updateCorreos(auth, new SocioCorreosPreferenciaDTO(true)));
        assertSame(rubros, perfilController.rubros());

        assertSame(directorio, new SocioDirectorioController(service).list(auth));
        var paginaDirectorio = new uy.ccisj.api.common.PageResponse<>(directorio, 0, 20, 1, 1);
        when(service.pageDirectorio("socio@example.test", new uy.ccisj.api.common.ListQuery("", 0, 20),
                uy.ccisj.api.domain.EstadoMorosidad.AL_DIA)).thenReturn(paginaDirectorio);
        assertSame(paginaDirectorio, new SocioDirectorioController(service)
                .page(auth, null, uy.ccisj.api.domain.EstadoMorosidad.AL_DIA, null, null));
    }

    @Test
    void tarifaControllerDelegaListadoGuardadoYFacturacion() {
        TarifaService service = mock(TarifaService.class);
        TarifaController controller = new TarifaController(service);
        var tarifa = new TarifaDTO(1L, 2026, BigDecimal.TEN, 10, null);
        var guardar = new GuardarTarifaDTO(2026, BigDecimal.TEN, 10);
        var generar = new GenerarFacturacionDTO(LocalDate.of(2026, 5, 1));
        var generada = new FacturacionGeneradaDTO(LocalDate.of(2026, 5, 1), 1L, 3, 2, 1);
        when(service.listar()).thenReturn(List.of(tarifa));
        when(service.guardar(guardar)).thenReturn(tarifa);
        when(service.generarFacturacion(generar)).thenReturn(generada);

        assertEquals(List.of(tarifa), controller.listar());
        assertSame(tarifa, controller.guardar(guardar));
        assertSame(generada, controller.generarFacturacion(generar));
    }

    @Test
    void historialContableControllerListaLosAsientosDelSocio() {
        HistorialContableService service = mock(HistorialContableService.class);
        HistorialContableController controller = new HistorialContableController(service);
        var asiento = new HistorialContableService.Asiento(1L, "CUOTA", 2L, "EMISION", BigDecimal.TEN,
                null, "ADMIN:a", "motivo", "{}");
        when(service.listar(4L)).thenReturn(List.of(asiento));

        assertEquals(List.of(asiento), controller.listar(4L));
    }

    @Test
    void adminAniversarioControllerEjecutaElProcesoDelDia() {
        AniversarioService service = mock(AniversarioService.class);
        var resultado = new AniversarioService.AniversarioRunResult(LocalDate.of(2026, 1, 1), 1, 1, 1, 0, 0);
        when(service.procesarAniversariosDelDia(null)).thenReturn(resultado);

        assertSame(resultado, new AdminAniversarioController(service).ejecutar());
    }
}

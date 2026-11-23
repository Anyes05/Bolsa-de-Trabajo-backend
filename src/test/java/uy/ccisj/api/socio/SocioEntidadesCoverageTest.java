package uy.ccisj.api.socio;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import uy.ccisj.api.catalogo.RubroEmpleo;
import uy.ccisj.api.domain.EstadoCuota;
import uy.ccisj.api.domain.EstadoMorosidad;
import uy.ccisj.api.domain.MetodoPago;
import uy.ccisj.api.user.Role;
import uy.ccisj.api.user.User;

/** Entidades JPA del módulo socio: valores por defecto, constructores y accesores. */
class SocioEntidadesCoverageTest {

    private static <T> T instanciarProtegido(Class<T> tipo) throws Exception {
        Constructor<T> constructor = tipo.getDeclaredConstructor();
        assertTrue(Modifier.isProtected(constructor.getModifiers()), "JPA requiere constructor protegido");
        constructor.setAccessible(true);
        return constructor.newInstance();
    }

    @Test
    void socioTieneValoresPorDefectoYAccesores() throws Exception {
        assertNotNull(instanciarProtegido(Socio.class));
        User user = new User("socio@example.test", "123", "hash", Role.SOCIO);
        Socio socio = new Socio(user, "Empresa");

        assertEquals(EstadoMorosidad.AL_DIA, socio.getEstadoMorosidad());
        assertEquals(LocalDate.now(), socio.getFechaAlta());
        assertTrue(socio.isAccesoHabilitado());
        assertFalse(socio.isSilenciarCorreos());
        assertFalse(socio.isImportadoMasivo());
        assertFalse(socio.isEsDirectivo());
        assertTrue(socio.getCuotas().isEmpty());
        assertTrue(socio.getOfertas().isEmpty());
        assertNull(socio.getId());
        assertSame(user, socio.getUsuario());

        User otro = new User("otro@example.test", "456", "hash", Role.SOCIO);
        Direccion direccion = new Direccion("Calle", "123", "San José");
        RubroEmpleo rubro = new RubroEmpleo("Comercio");
        socio.setUsuario(otro);
        socio.setDireccion(direccion);
        socio.setRubro(rubro);
        socio.setRazonSocial("Nueva");
        socio.setRut("211111110011");
        socio.setTelefono("099000000");
        socio.setEmailContacto("contacto@example.test");
        socio.setEstadoMorosidad(EstadoMorosidad.MOROSO);
        socio.setEsDirectivo(true);
        socio.setFechaAlta(LocalDate.of(2020, 1, 1));
        socio.setFechaAniversario(LocalDate.of(1990, 5, 5));
        socio.setCamposFicticios("[\"rut\"]");
        socio.setImportadoMasivo(true);
        socio.setAccesoHabilitado(false);
        socio.setSilenciarCorreos(true);

        assertSame(otro, socio.getUsuario());
        assertSame(direccion, socio.getDireccion());
        assertSame(rubro, socio.getRubro());
        assertEquals("Nueva", socio.getRazonSocial());
        assertEquals("211111110011", socio.getRut());
        assertEquals("099000000", socio.getTelefono());
        assertEquals("contacto@example.test", socio.getEmailContacto());
        assertEquals(EstadoMorosidad.MOROSO, socio.getEstadoMorosidad());
        assertTrue(socio.isEsDirectivo());
        assertEquals(LocalDate.of(2020, 1, 1), socio.getFechaAlta());
        assertEquals(LocalDate.of(1990, 5, 5), socio.getFechaAniversario());
        assertEquals("[\"rut\"]", socio.getCamposFicticios());
        assertTrue(socio.isImportadoMasivo());
        assertFalse(socio.isAccesoHabilitado());
        assertTrue(socio.isSilenciarCorreos());
    }

    @Test
    void direccionPermiteEditarSusCampos() throws Exception {
        assertNotNull(instanciarProtegido(Direccion.class));
        Direccion direccion = new Direccion("18 de Julio", "500", "San José de Mayo");
        assertNull(direccion.getId());
        assertEquals("18 de Julio", direccion.getCalle());
        assertEquals("500", direccion.getNumero());
        assertEquals("San José de Mayo", direccion.getLocalidad());

        direccion.setCalle("Artigas");
        direccion.setNumero(null);
        direccion.setLocalidad("Libertad");

        assertEquals("Artigas", direccion.getCalle());
        assertNull(direccion.getNumero());
        assertEquals("Libertad", direccion.getLocalidad());
    }

    @Test
    void cuotaArrancaPendienteSinTimbreYPermiteEditar() throws Exception {
        assertNotNull(instanciarProtegido(Cuota.class));
        Socio socio = new Socio(null, "Empresa");
        Cuota cuota = new Cuota(socio, LocalDate.of(2026, 3, 1), BigDecimal.TEN, LocalDate.of(2026, 4, 30));

        assertNull(cuota.getId());
        assertEquals(EstadoCuota.PENDIENTE, cuota.getEstado());
        assertEquals(0, cuota.getMontoTimbre().signum());
        assertFalse(cuota.isTimbreRecurrente());
        assertTrue(cuota.getPagos().isEmpty());
        assertNull(cuota.getTarifa());

        Socio otro = new Socio(null, "Otra");
        Tarifa tarifa = new Tarifa(2026, BigDecimal.TEN, 10);
        Pago pago = new Pago(cuota, BigDecimal.ONE, LocalDate.of(2026, 3, 2), MetodoPago.EFECTIVO);
        cuota.setSocio(otro);
        cuota.setTarifa(tarifa);
        cuota.setPeriodo(LocalDate.of(2026, 5, 1));
        cuota.setMonto(new BigDecimal("20"));
        cuota.setMontoTimbre(new BigDecimal("3"));
        cuota.setTimbreRecurrente(true);
        cuota.setFechaVencimiento(LocalDate.of(2026, 6, 30));
        cuota.setEstado(EstadoCuota.PAGADO);
        cuota.setPagos(List.of(pago));

        assertSame(otro, cuota.getSocio());
        assertSame(tarifa, cuota.getTarifa());
        assertEquals(LocalDate.of(2026, 5, 1), cuota.getPeriodo());
        assertEquals(new BigDecimal("20"), cuota.getMonto());
        assertEquals(new BigDecimal("3"), cuota.getMontoTimbre());
        assertTrue(cuota.isTimbreRecurrente());
        assertEquals(LocalDate.of(2026, 6, 30), cuota.getFechaVencimiento());
        assertEquals(EstadoCuota.PAGADO, cuota.getEstado());
        assertEquals(List.of(pago), cuota.getPagos());
    }

    @Test
    void pagoDesdeCuotaHeredaElSocioYPermiteEditar() throws Exception {
        Pago vacio = instanciarProtegido(Pago.class);
        assertEquals(MetodoPago.EFECTIVO, vacio.getMetodoPago(), "método por defecto");

        Socio socio = new Socio(null, "Empresa");
        Cuota cuota = new Cuota(socio, LocalDate.of(2026, 3, 1), BigDecimal.TEN, LocalDate.of(2026, 4, 30));
        Pago pago = new Pago(cuota, BigDecimal.TEN, LocalDate.of(2026, 3, 5), MetodoPago.EFECTIVO);

        assertSame(socio, pago.getSocio());
        assertSame(cuota, pago.getCuota());
        assertNull(pago.getId());
        assertNull(pago.getCreatedAt());
        assertFalse(pago.isModificadoAdmin());

        Socio otro = new Socio(null, "Otra");
        pago.setSocio(otro);
        pago.setCuota(null);
        pago.setMontoCobrado(new BigDecimal("7"));
        pago.setFechaEmision(LocalDate.of(2026, 3, 6));
        pago.setNroCobranzaExterno("EXT-1");
        pago.setObservaciones("Obs");
        pago.setModificadoAdmin(true);
        pago.setMetodoPago(MetodoPago.EFECTIVO);

        assertSame(otro, pago.getSocio());
        assertNull(pago.getCuota());
        assertEquals(new BigDecimal("7"), pago.getMontoCobrado());
        assertEquals(LocalDate.of(2026, 3, 6), pago.getFechaEmision());
        assertEquals("EXT-1", pago.getNroCobranzaExterno());
        assertEquals("Obs", pago.getObservaciones());
        assertTrue(pago.isModificadoAdmin());
        assertEquals(MetodoPago.EFECTIVO, pago.getMetodoPago());
    }

    @Test
    void tarifaPermiteEditarMontoYVencimiento() throws Exception {
        assertNotNull(instanciarProtegido(Tarifa.class));
        Tarifa tarifa = new Tarifa(2026, BigDecimal.TEN, 10);
        assertNull(tarifa.getId());
        assertNull(tarifa.getCreatedAt());

        tarifa.setAnio(2027);
        tarifa.setMontoBase(new BigDecimal("15"));
        tarifa.setDiaVencimiento(20);

        assertEquals(2027, tarifa.getAnio());
        assertEquals(new BigDecimal("15"), tarifa.getMontoBase());
        assertEquals(20, tarifa.getDiaVencimiento());
    }

    @Test
    void reintegroNormalizaElTipo() throws Exception {
        Reintegro vacio = instanciarProtegido(Reintegro.class);
        assertEquals("PUNTUAL", vacio.getTipo());

        Socio socio = new Socio(null, "Empresa");
        LocalDate fecha = LocalDate.of(2026, 3, 1);
        Reintegro recurrente = new Reintegro(socio, "Timbre", BigDecimal.ONE, fecha,
                fecha.plusMonths(1), "recurrente:1:2026-03-01", " recurrente ");
        Reintegro sinTipo = new Reintegro(socio, "Gasto", BigDecimal.TEN, fecha, fecha, null, null);
        Reintegro tipoEnBlanco = new Reintegro(socio, "Gasto", BigDecimal.TEN, fecha, fecha, null, "  ");

        assertEquals("RECURRENTE", recurrente.getTipo());
        assertTrue(recurrente.isRecurrente());
        assertEquals("PUNTUAL", sinTipo.getTipo());
        assertFalse(sinTipo.isRecurrente());
        assertEquals("PUNTUAL", tipoEnBlanco.getTipo());
        assertNull(recurrente.getId());
        assertSame(socio, recurrente.getSocio());
        assertNull(recurrente.getCuota());
        assertEquals("Timbre", recurrente.getConcepto());
        assertEquals(BigDecimal.ONE, recurrente.getMonto());
        assertEquals(fecha, recurrente.getFecha());
        assertEquals(fecha.plusMonths(1), recurrente.getFechaVencimiento());
        assertEquals("recurrente:1:2026-03-01", recurrente.getOrigen());
    }

    @Test
    void reintegroProgramadoArrancaActivoYPermiteEditar() throws Exception {
        assertTrue(instanciarProtegido(ReintegroProgramado.class).isActivo());
        Socio socio = new Socio(null, "Empresa");
        ReintegroProgramado programado = new ReintegroProgramado(socio, "Timbre", BigDecimal.ONE,
                LocalDate.of(2026, 3, 1));
        assertNull(programado.getId());
        assertSame(socio, programado.getSocio());

        programado.setConcepto("Gasto");
        programado.setMonto(BigDecimal.TEN);
        programado.setActivo(false);
        programado.setDesdePeriodo(LocalDate.of(2026, 4, 1));

        assertEquals("Gasto", programado.getConcepto());
        assertEquals(BigDecimal.TEN, programado.getMonto());
        assertFalse(programado.isActivo());
        assertEquals(LocalDate.of(2026, 4, 1), programado.getDesdePeriodo());
    }

    @Test
    void reglasYSaldoSonClasesUtilitarias() throws Exception {
        for (Class<?> tipo : List.of(MorosidadRules.class, SaldoCuenta.class)) {
            Constructor<?> constructor = tipo.getDeclaredConstructor();
            assertTrue(Modifier.isPrivate(constructor.getModifiers()));
            constructor.setAccessible(true);
            assertNotNull(constructor.newInstance());
        }
    }

    @Test
    void morosidadContemplaSocioNuloUsuarioNuloYDeudaNula() {
        assertEquals(EstadoMorosidad.AL_DIA, MorosidadRules.desdeDeuda(null, BigDecimal.TEN));
        Socio sinUsuario = new Socio(null, "Empresa");
        assertEquals(EstadoMorosidad.AL_DIA, MorosidadRules.desdeDeuda(sinUsuario, null));
        assertEquals(EstadoMorosidad.AL_DIA, MorosidadRules.desdeDeuda(sinUsuario, BigDecimal.ZERO));
        assertEquals(EstadoMorosidad.MOROSO, MorosidadRules.desdeDeuda(sinUsuario, BigDecimal.ONE));
        User inactivo = new User("x@example.test", "1", "hash", Role.SOCIO);
        inactivo.setActivo(false);
        assertEquals(EstadoMorosidad.INACTIVO, MorosidadRules.desdeDeuda(new Socio(inactivo, "X"), BigDecimal.ZERO));
        assertFalse(MorosidadRules.puedeIngresarAlSistema(null));
        assertFalse(MorosidadRules.puedeIngresarAlSistema(inactivo));
        ReflectionTestUtils.setField(inactivo, "activo", true);
        assertTrue(MorosidadRules.puedeIngresarAlSistema(inactivo));
    }
}

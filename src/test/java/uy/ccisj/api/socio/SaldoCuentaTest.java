package uy.ccisj.api.socio;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class SaldoCuentaTest {

    @Test
    void vencimientoEsUltimoDiaDelMesSiguiente() {
        assertEquals(LocalDate.of(2026, 12, 31), SaldoCuenta.vencimiento(LocalDate.of(2026, 11, 1)));
    }

    @Test
    void cuotaNoVencidaNoCuentaComoDeudaVencida() {
        LocalDate hoy = LocalDate.of(2026, 11, 9);
        var cargos = List.of(new SaldoCuenta.Cargo(
                "c1",
                LocalDate.of(2026, 11, 1),
                LocalDate.of(2026, 12, 31),
                new BigDecimal("2600")));

        SaldoCuenta.Resultado resultado = SaldoCuenta.calcular(cargos, BigDecimal.ZERO, hoy);

        assertEquals(0, new BigDecimal("2600").compareTo(resultado.saldo()));
        assertEquals(0, BigDecimal.ZERO.compareTo(resultado.deudaVencida()));
    }

    @Test
    void cuotaVencidaImpagaCuentaComoDeudaVencida() {
        LocalDate hoy = LocalDate.of(2026, 11, 9);
        var cargos = List.of(new SaldoCuenta.Cargo(
                "c1",
                LocalDate.of(2026, 9, 1),
                LocalDate.of(2026, 10, 31),
                new BigDecimal("2600")));

        SaldoCuenta.Resultado resultado = SaldoCuenta.calcular(cargos, BigDecimal.ZERO, hoy);

        assertEquals(0, new BigDecimal("2600").compareTo(resultado.deudaVencida()));
    }

    @Test
    void pagosReducenDeudaVencida() {
        LocalDate hoy = LocalDate.of(2026, 11, 9);
        var cargos = List.of(new SaldoCuenta.Cargo(
                "c1",
                LocalDate.of(2026, 9, 1),
                LocalDate.of(2026, 10, 31),
                new BigDecimal("2600")));

        SaldoCuenta.Resultado resultado = SaldoCuenta.calcular(cargos, new BigDecimal("2600"), hoy);

        assertEquals(0, BigDecimal.ZERO.compareTo(resultado.saldo()));
        assertEquals(0, BigDecimal.ZERO.compareTo(resultado.deudaVencida()));
    }
}

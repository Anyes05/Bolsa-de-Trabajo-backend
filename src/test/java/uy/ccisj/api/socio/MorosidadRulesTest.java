package uy.ccisj.api.socio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import uy.ccisj.api.domain.EstadoMorosidad;
import uy.ccisj.api.user.Role;
import uy.ccisj.api.user.User;

class MorosidadRulesTest {

    @Test
    void deudaVencidaMarcaMorosoPeroSiguePudiendoIngresar() {
        User user = new User(null, "1", "hash", Role.SOCIO);
        Socio socio = new Socio(user, "Demo");
        socio.setEstadoMorosidad(EstadoMorosidad.AL_DIA);

        assertEquals(EstadoMorosidad.MOROSO, MorosidadRules.desdeDeuda(socio, new BigDecimal("100.00")));
        assertTrue(MorosidadRules.puedeIngresarAlSistema(user));
    }

    @Test
    void sinDeudaVencidaQuedaAlDiaAunqueTengaSaldoDelMes() {
        User user = new User(null, "1", "hash", Role.SOCIO);
        Socio socio = new Socio(user, "Demo");

        assertEquals(EstadoMorosidad.AL_DIA, MorosidadRules.desdeDeuda(socio, BigDecimal.ZERO));
    }

    @Test
    void usuarioInactivoSiempreEsInactivoEnCaja() {
        User user = new User(null, "1", "hash", Role.SOCIO);
        user.setActivo(false);
        Socio socio = new Socio(user, "Demo");
        socio.setEstadoMorosidad(EstadoMorosidad.MOROSO);

        assertEquals(EstadoMorosidad.INACTIVO, MorosidadRules.desdeDeuda(socio, new BigDecimal("500")));
        assertFalse(MorosidadRules.puedeIngresarAlSistema(user));
    }

    @Test
    void estadoInactivoEnSocioGanaAunqueUsuarioSigaActivo() {
        User user = new User(null, "1", "hash", Role.SOCIO);
        Socio socio = new Socio(user, "Demo");
        socio.setEstadoMorosidad(EstadoMorosidad.INACTIVO);

        assertEquals(EstadoMorosidad.INACTIVO, MorosidadRules.desdeDeuda(socio, BigDecimal.ZERO));
    }
}

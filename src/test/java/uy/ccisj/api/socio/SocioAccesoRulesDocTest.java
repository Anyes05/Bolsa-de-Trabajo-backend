package uy.ccisj.api.socio;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import uy.ccisj.api.domain.EstadoMorosidad;
import uy.ccisj.api.user.Role;
import uy.ccisj.api.user.User;

/**
 * Documenta la regla de producto: moroso entra; baja no.
 * (deactivateSocio pone activo=false; el login filtra User::isActivo.)
 */
class SocioAccesoRulesDocTest {

    @Test
    void morosoConUsuarioActivoPuedeIngresar() {
        User user = new User(null, "111", "hash", Role.SOCIO);
        Socio socio = new Socio(user, "Moroso SA");
        socio.setEstadoMorosidad(EstadoMorosidad.MOROSO);
        assertTrue(user.isActivo());
        assertTrue(MorosidadRules.puedeIngresarAlSistema(user));
    }

    @Test
    void bajaDesactivaUsuarioYBloqueaIngreso() {
        User user = new User(null, "222", "hash", Role.SOCIO);
        Socio socio = new Socio(user, "Baja SA");
        // Equivalente a markInactive en SocioService
        socio.setEstadoMorosidad(EstadoMorosidad.INACTIVO);
        user.setActivo(false);
        assertFalse(MorosidadRules.puedeIngresarAlSistema(user));
    }
}

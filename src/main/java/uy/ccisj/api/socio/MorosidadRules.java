package uy.ccisj.api.socio;

import java.math.BigDecimal;
import uy.ccisj.api.domain.EstadoMorosidad;
import uy.ccisj.api.user.User;

/**
 * Reglas de estado de cobranza (sin recargos por mora: decisión de producto).
 */
public final class MorosidadRules {
    private MorosidadRules() {}

    /**
     * Solo la baja (usuario inactivo o estado INACTIVO) bloquea el acceso al sistema.
     * Un socio MOROSO sigue pudiendo ingresar.
     */
    public static boolean puedeIngresarAlSistema(User user) {
        return user != null && user.isActivo();
    }

    public static EstadoMorosidad desdeDeuda(Socio socio, BigDecimal deudaVencida) {
        if (socio == null) {
            return EstadoMorosidad.AL_DIA;
        }
        User user = socio.getUsuario();
        if (socio.getEstadoMorosidad() == EstadoMorosidad.INACTIVO
                || (user != null && !user.isActivo())) {
            return EstadoMorosidad.INACTIVO;
        }
        if (deudaVencida != null && deudaVencida.signum() > 0) {
            return EstadoMorosidad.MOROSO;
        }
        return EstadoMorosidad.AL_DIA;
    }
}

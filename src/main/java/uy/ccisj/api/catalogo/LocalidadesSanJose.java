package uy.ccisj.api.catalogo;

import java.util.List;
import java.util.Set;

public final class LocalidadesSanJose {

    public static final List<String> LOCALIDADES = List.of(
            "San José de Mayo",
            "Ciudad del Plata",
            "Libertad",
            "Rodríguez",
            "Ecilda Paullier",
            "Puntas de Valdez",
            "Rafael Perazza",
            "Kiyú-Ordeig",
            "Villa María",
            "Raigón",
            "Juan Soler",
            "Mal Abrigo",
            "Capurro",
            "Ituzaingó",
            "18 de Julio",
            "Monte Grande",
            "Otra localidad");

    private static final Set<String> VALIDAS = Set.copyOf(LOCALIDADES);

    private LocalidadesSanJose() {
    }

    public static boolean isValid(String localidad) {
        return localidad != null && VALIDAS.contains(localidad.trim());
    }
}

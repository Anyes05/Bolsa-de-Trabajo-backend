package uy.ccisj.api.socio.importacion;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class CamposFicticios {
    public static final String BPS = "bps";
    public static final String RUT = "rut";
    public static final String TELEFONO = "telefono";
    public static final String EMAIL_CONTACTO = "emailContacto";
    public static final String EMAIL_USUARIO = "emailUsuario";
    public static final String CALLE = "calle";
    public static final String NUMERO = "numero";
    public static final String LOCALIDAD = "localidad";
    public static final String RUBRO = "rubro";
    public static final String FECHA_ALTA = "fechaAlta";
    public static final String FECHA_ANIVERSARIO = "fechaAniversario";

    private CamposFicticios() {}

    public static Set<String> parse(String stored) {
        Set<String> values = new LinkedHashSet<>();
        if (stored == null || stored.isBlank()) {
            return values;
        }
        for (String part : stored.split(",")) {
            String value = part.trim();
            if (!value.isEmpty()) {
                values.add(value);
            }
        }
        return values;
    }

    public static String serialize(Set<String> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        return String.join(",", values);
    }

    public static List<String> list(String stored) {
        return new ArrayList<>(parse(stored));
    }

    public static boolean tiene(String stored, String campo) {
        return parse(stored).contains(campo);
    }
}

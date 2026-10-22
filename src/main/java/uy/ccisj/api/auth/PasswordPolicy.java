package uy.ccisj.api.auth;

import java.security.SecureRandom;
import java.text.Normalizer;

public final class PasswordPolicy {

    public static final String REGEX = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9]).{8,72}$";
    public static final String MESSAGE = "La contraseña debe tener al menos 8 caracteres, una mayúscula, una minúscula, un número y un carácter especial";

    private static final String SPECIALS = "!@#$%&*?._-";
    private static final SecureRandom RANDOM = new SecureRandom();

    private PasswordPolicy() {
    }

    public static boolean isValid(String password) {
        return password != null && password.matches(REGEX);
    }

    /**
     * Genera la clave provisional de un socio a partir de su razón social.
     * Ejemplo: "Distribuidora Fernández Hnos." -> "Distribuidora#4821"
     */
    public static String generateProvisional(String razonSocial) {
        String letters = Normalizer.normalize(razonSocial == null ? "" : razonSocial, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replaceAll("[^A-Za-z]", "");
        if (letters.length() < 4) {
            letters = (letters + "Socio").substring(0, Math.max(4, letters.length()));
            if (letters.length() < 4) {
                letters = "Socio";
            }
        }
        String base = letters.substring(0, Math.min(10, letters.length()));
        base = Character.toUpperCase(base.charAt(0)) + base.substring(1).toLowerCase();
        char special = SPECIALS.charAt(RANDOM.nextInt(SPECIALS.length()));
        int digits = 1000 + RANDOM.nextInt(9000);
        return base + special + digits;
    }
}

package uy.ccisj.api.auth;

import java.security.SecureRandom;

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
     * Clave provisional aleatoria (no se deriva de la razón social).
     * Cumple la política: mayúscula, minúscula, número y símbolo.
     */
    public static String generateProvisional(String ignoredRazonSocial) {
        return generateProvisional();
    }

    public static String generateProvisional() {
        String upper = "ABCDEFGHJKLMNPQRSTUVWXYZ";
        String lower = "abcdefghijkmnopqrstuvwxyz";
        String digits = "23456789";
        StringBuilder builder = new StringBuilder(14);
        builder.append(upper.charAt(RANDOM.nextInt(upper.length())));
        builder.append(lower.charAt(RANDOM.nextInt(lower.length())));
        builder.append(digits.charAt(RANDOM.nextInt(digits.length())));
        builder.append(SPECIALS.charAt(RANDOM.nextInt(SPECIALS.length())));
        String all = upper + lower + digits + SPECIALS;
        while (builder.length() < 14) {
            builder.append(all.charAt(RANDOM.nextInt(all.length())));
        }
        char[] chars = builder.toString().toCharArray();
        for (int index = chars.length - 1; index > 0; index--) {
            int swap = RANDOM.nextInt(index + 1);
            char tmp = chars[index];
            chars[index] = chars[swap];
            chars[swap] = tmp;
        }
        return new String(chars);
    }
}

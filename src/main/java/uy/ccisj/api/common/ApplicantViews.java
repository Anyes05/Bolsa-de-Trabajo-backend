package uy.ccisj.api.common;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public final class ApplicantViews {
    private ApplicantViews() {}

    public static List<String> splitRubros(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return Arrays.stream(value.split("\\|"))
                .filter(item -> !item.isBlank())
                .toList();
    }

    public static String initials(String fullName) {
        if (fullName == null || fullName.isBlank()) {
            return "";
        }
        return Arrays.stream(fullName.split("\\s+"))
                .filter(part -> !part.isBlank())
                .limit(2)
                .map(part -> part.substring(0, 1).toUpperCase(Locale.ROOT))
                .collect(Collectors.joining());
    }

    public static String cvMeta(long sizeBytes, boolean wasNull) {
        if (wasNull || sizeBytes <= 0) {
            return "PDF oficial";
        }
        if (sizeBytes < 1024 * 1024) {
            return "PDF oficial · " + Math.max(1, Math.round(sizeBytes / 1024.0)) + " KB";
        }
        return "PDF oficial · " + String.format(Locale.ROOT, "%.1f MB", sizeBytes / (1024.0 * 1024.0));
    }
}

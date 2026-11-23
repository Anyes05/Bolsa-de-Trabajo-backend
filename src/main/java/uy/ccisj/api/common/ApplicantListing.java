package uy.ccisj.api.common;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * Búsqueda, filtros y paginación de perfiles de postulantes en el servidor.
 * Se pagina por postulante (grupo de perfiles) para que una persona no quede partida entre páginas.
 */
public final class ApplicantListing {
    private static final Set<String> SIN_LIBRETA =
            Set.of("", "no", "sin libreta", "no tiene", "no tiene libreta", "no informada");

    private ApplicantListing() {}

    /** Filtros comunes de la bolsa. {@code libreta}: "yes" | "no" | null. */
    public record Filters(String rubro, String disponibilidad, String libreta) {
        public static Filters of(String rubro, String disponibilidad, String libreta) {
            if (libreta != null && !libreta.isBlank() && !"yes".equals(libreta) && !"no".equals(libreta)) {
                throw new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.BAD_REQUEST, "El filtro de libreta debe ser 'yes' o 'no'.");
            }
            return new Filters(blankToNull(rubro), blankToNull(disponibilidad), blankToNull(libreta));
        }
    }

    /** Vista común de un perfil, para no acoplar el helper a cada DTO. */
    public record Fields(Long applicantId, String fullName, String email, String phone, String identityCard,
                         String profileName, List<String> sectors, String availability, String license) {}

    public record Group<T>(Long applicantId, List<T> profiles) {}

    public record Page<T>(PageResponse<Group<T>> pagina, long perfiles, List<String> rubros,
                          List<String> disponibilidades) {}

    public static boolean hasLicense(String license) {
        return license != null && !SIN_LIBRETA.contains(license.strip().toLowerCase(Locale.ROOT));
    }

    public static boolean matches(Fields f, ListQuery query, Filters filters) {
        return query.matches(f.fullName(), f.email(), f.phone(), f.identityCard(), f.profileName())
                && (filters.rubro() == null || (f.sectors() != null && f.sectors().contains(filters.rubro())))
                && (filters.disponibilidad() == null || filters.disponibilidad().equals(f.availability()))
                && (filters.libreta() == null || hasLicense(f.license()) == "yes".equals(filters.libreta()));
    }

    public static <T> Page<T> page(List<T> all, List<T> filtered, Function<T, Fields> fields, ListQuery query) {
        Map<Long, List<T>> groups = new LinkedHashMap<>();
        for (T item : filtered) {
            groups.computeIfAbsent(fields.apply(item).applicantId(), id -> new ArrayList<>()).add(item);
        }
        List<Group<T>> grouped = groups.entrySet().stream().map(e -> new Group<>(e.getKey(), List.copyOf(e.getValue()))).toList();
        List<String> rubros = all.stream().flatMap(item -> safe(fields.apply(item).sectors()).stream())
                .filter(value -> value != null && !value.isBlank()).distinct().sorted().toList();
        List<String> disponibilidades = all.stream().map(item -> fields.apply(item).availability())
                .filter(value -> value != null && !value.isBlank()).distinct().sorted().toList();
        return new Page<>(query.paginate(grouped), filtered.size(), rubros, disponibilidades);
    }

    private static List<String> safe(List<String> values) {
        return values == null ? List.of() : values;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}

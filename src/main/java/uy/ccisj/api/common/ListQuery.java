package uy.ccisj.api.common;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * Búsqueda y paginación validadas para listados.
 * Límites estrictos para evitar páginas gigantes y términos abusivos.
 */
public record ListQuery(String q, int page, int size) {
    public static final int DEFAULT_SIZE = 20;
    public static final int MAX_SIZE = 100;
    public static final int MAX_QUERY_LENGTH = 100;
    private static final Pattern DIACRITICS = Pattern.compile("\\p{M}+");

    public static ListQuery of(String q, Integer page, Integer size) {
        int p = page == null ? 0 : page;
        int s = size == null ? DEFAULT_SIZE : size;
        if (p < 0) throw badRequest("La página no puede ser negativa.");
        if (s < 1 || s > MAX_SIZE) throw badRequest("El tamaño de página debe estar entre 1 y " + MAX_SIZE + ".");
        String term = q == null ? "" : q.strip();
        if (term.length() > MAX_QUERY_LENGTH) {
            throw badRequest("La búsqueda no puede superar " + MAX_QUERY_LENGTH + " caracteres.");
        }
        return new ListQuery(term, p, s);
    }

    public boolean hasTerm() {
        return !q.isEmpty();
    }

    /** Coincidencia parcial sin distinguir mayúsculas ni tildes en cualquiera de los campos. */
    public boolean matches(String... values) {
        if (!hasTerm()) return true;
        String needle = normalize(q);
        return Arrays.stream(values).filter(Objects::nonNull).map(ListQuery::normalize).anyMatch(v -> v.contains(needle));
    }

    public <T> PageResponse<T> paginate(List<T> items) {
        long total = items.size();
        int totalPages = (int) Math.ceil(total / (double) size);
        int from = (int) Math.min((long) page * size, total);
        int to = (int) Math.min((long) from + size, total);
        return new PageResponse<>(items.subList(from, to), page, size, total, totalPages);
    }

    public <T> PageResponse<T> filterAndPaginate(List<T> items, Predicate<T> filter) {
        return paginate(items.stream().filter(filter).toList());
    }

    public static String normalize(String value) {
        return DIACRITICS.matcher(Normalizer.normalize(value, Normalizer.Form.NFD)).replaceAll("").toLowerCase(Locale.ROOT);
    }

    private static ResponseStatusException badRequest(String detail) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, detail);
    }
}

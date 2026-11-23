package uy.ccisj.api.common;

import java.util.List;

/** Página de un listado: búsqueda y filtros ya aplicados en el servidor. */
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages) {

    public PageResponse {
        content = List.copyOf(content);
    }
}

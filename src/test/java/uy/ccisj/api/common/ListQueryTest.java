package uy.ccisj.api.common;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class ListQueryTest {

    @Test
    void appliesDefaultsAndTrimsTerm() {
        var query = ListQuery.of("  acme ", null, null);
        assertEquals("acme", query.q());
        assertEquals(0, query.page());
        assertEquals(ListQuery.DEFAULT_SIZE, query.size());
    }

    @Test
    void rejectsInvalidPagingAndOversizedTerms() {
        assertEquals(400, assertThrows(ResponseStatusException.class, () -> ListQuery.of(null, -1, 10)).getStatusCode().value());
        assertEquals(400, assertThrows(ResponseStatusException.class, () -> ListQuery.of(null, 0, 0)).getStatusCode().value());
        assertEquals(400, assertThrows(ResponseStatusException.class, () -> ListQuery.of(null, 0, ListQuery.MAX_SIZE + 1)).getStatusCode().value());
        assertEquals(400, assertThrows(ResponseStatusException.class,
                () -> ListQuery.of("x".repeat(ListQuery.MAX_QUERY_LENGTH + 1), 0, 10)).getStatusCode().value());
    }

    @Test
    void matchesIgnoringCaseAccentsAndNulls() {
        var query = ListQuery.of("jose", 0, 10);
        assertTrue(query.matches(null, "Panadería San JOSÉ"));
        assertFalse(query.matches(null, "Montevideo"));
        assertTrue(ListQuery.of("", 0, 10).matches((String) null));
    }

    @Test
    void paginatesAndReportsTotals() {
        List<Integer> items = IntStream.rangeClosed(1, 45).boxed().toList();
        var second = ListQuery.of(null, 1, 20).paginate(items);
        assertEquals(21, second.content().get(0));
        assertEquals(20, second.content().size());
        assertEquals(45, second.totalElements());
        assertEquals(3, second.totalPages());
        assertEquals(5, ListQuery.of(null, 2, 20).paginate(items).content().size());
        var beyond = ListQuery.of(null, 9, 20).paginate(items);
        assertTrue(beyond.content().isEmpty());
        assertEquals(45, beyond.totalElements());
    }

    @Test
    void filtersBeforePaginating() {
        List<Integer> items = IntStream.rangeClosed(1, 30).boxed().toList();
        var page = ListQuery.of(null, 0, 5).filterAndPaginate(items, n -> n % 2 == 0);
        assertEquals(List.of(2, 4, 6, 8, 10), page.content());
        assertEquals(15, page.totalElements());
    }
}

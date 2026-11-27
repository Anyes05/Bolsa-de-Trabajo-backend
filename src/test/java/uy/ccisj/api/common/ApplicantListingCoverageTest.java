package uy.ccisj.api.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class ApplicantListingCoverageTest {

    @Test
    void lasOpcionesDeFiltroIgnoranRubrosYDisponibilidadesVaciosONulos() {
        List<ApplicantListing.Fields> catalogo = List.of(
                new ApplicantListing.Fields(1L, "Ana", "a@x.test", "1", "1", "P1", Arrays.asList("Ventas", null, " "), "FULL_TIME", null),
                new ApplicantListing.Fields(2L, "Bruno", "b@x.test", "2", "2", "P2", null, " ", null),
                new ApplicantListing.Fields(3L, "Carla", "c@x.test", "3", "3", "P3", List.of("Logística"), null, null));

        ApplicantListing.Page<ApplicantListing.Fields> pagina =
                ApplicantListing.page(catalogo, catalogo, perfil -> perfil, ListQuery.of(null, 0, 20));

        assertEquals(List.of("Logística", "Ventas"), pagina.rubros());
        assertEquals(List.of("FULL_TIME"), pagina.disponibilidades());
        assertEquals(3, pagina.perfiles());
        assertEquals(3, pagina.pagina().content().size());
    }

    @Test
    void filtrosEnBlancoSeIgnoranYUnPerfilSinRubrosNoCoincideConElRubroPedido() {
        ApplicantListing.Filters vacios = ApplicantListing.Filters.of("  ", " ", "   ");
        assertNull(vacios.rubro());
        assertNull(vacios.disponibilidad());
        assertNull(vacios.libreta());

        ApplicantListing.Fields sinRubros =
                new ApplicantListing.Fields(1L, "Ana", "a@x.test", "1", "1", "P1", null, "FULL_TIME", null);
        assertFalse(ApplicantListing.matches(sinRubros, ListQuery.of(null, 0, 20),
                ApplicantListing.Filters.of("Ventas", null, null)));
    }
}

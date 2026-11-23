package uy.ccisj.api.common;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class ApplicantListingTest {

    private static ApplicantListing.Fields perfil(long applicantId, String nombre, String rubro, String disponibilidad,
                                                  String libreta) {
        return new ApplicantListing.Fields(applicantId, nombre, nombre.toLowerCase() + "@example.test", "099",
                "1234567-8", "Perfil " + rubro, List.of(rubro), disponibilidad, libreta);
    }

    private final List<ApplicantListing.Fields> perfiles = List.of(
            perfil(1, "Ana Pérez", "Ventas", "FULL_TIME", "Categoría A"),
            perfil(1, "Ana Pérez", "Logística", "PART_TIME", "Categoría A"),
            perfil(2, "Bruno Díaz", "Ventas", "PART_TIME", "No tiene"),
            perfil(3, "Carla Gómez", "Gastronomía", "FULL_TIME", null));

    private List<ApplicantListing.Fields> filtrar(String q, String rubro, String disp, String libreta) {
        ListQuery query = ListQuery.of(q, 0, 20);
        ApplicantListing.Filters filters = ApplicantListing.Filters.of(rubro, disp, libreta);
        return perfiles.stream().filter(p -> ApplicantListing.matches(p, query, filters)).toList();
    }

    @Test
    void filtersBySearchSectorAvailabilityAndLicense() {
        assertEquals(2, filtrar("perez", null, null, null).size(), "busca sin tildes ni mayúsculas");
        assertEquals(2, filtrar(null, "Ventas", null, null).size());
        assertEquals(2, filtrar(null, null, "PART_TIME", null).size());
        assertEquals(2, filtrar(null, null, null, "yes").size());
        assertEquals(2, filtrar(null, null, null, "no").size(), "'No tiene' y null cuentan como sin libreta");
        assertEquals(1, filtrar("ana", "Ventas", "FULL_TIME", "yes").size());
    }

    @Test
    void rejectsUnknownLicenseFilter() {
        assertEquals(400, assertThrows(ResponseStatusException.class,
                () -> ApplicantListing.Filters.of(null, null, "maybe")).getStatusCode().value());
    }

    @Test
    void paginatesByApplicantAndReturnsOptionsFromWholeCatalog() {
        var page = ApplicantListing.page(perfiles, perfiles, f -> f, ListQuery.of(null, 0, 2));
        assertEquals(3, page.pagina().totalElements(), "se cuenta por postulante, no por perfil");
        assertEquals(2, page.pagina().content().size());
        assertEquals(2, page.pagina().content().get(0).profiles().size(), "los perfiles de Ana no se parten");
        assertEquals(4, page.perfiles());
        assertEquals(List.of("Gastronomía", "Logística", "Ventas"), page.rubros());
        assertEquals(List.of("FULL_TIME", "PART_TIME"), page.disponibilidades());

        var segunda = ApplicantListing.page(perfiles, perfiles, f -> f, ListQuery.of(null, 1, 2));
        assertEquals(List.of(3L), segunda.pagina().content().stream().map(ApplicantListing.Group::applicantId).toList());
    }
}

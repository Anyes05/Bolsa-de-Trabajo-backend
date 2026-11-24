package uy.ccisj.api.oferta;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import uy.ccisj.api.catalogo.RubroEmpleo;
import uy.ccisj.api.catalogo.RubroEmpleoRepository;
import uy.ccisj.api.domain.DisponibilidadHoraria;
import uy.ccisj.api.domain.EstadoOferta;
import uy.ccisj.api.domain.TipoContrato;
import uy.ccisj.api.socio.Socio;
import uy.ccisj.api.socio.SocioRepository;
import uy.ccisj.api.user.Role;
import uy.ccisj.api.user.User;
import uy.ccisj.api.user.UserRepository;

class OfertaEmpleoServiceTest {
    /** Misma zona que OfertaEmpleoService. En CI (UTC) el día calendario no coincide con Montevideo. */
    private static final ZoneId APP_ZONE = ZoneId.of("America/Montevideo");

    private final OfertaEmpleoRepository ofertas = mock(OfertaEmpleoRepository.class);
    private final SocioRepository socios = mock(SocioRepository.class);
    private final RubroEmpleoRepository rubros = mock(RubroEmpleoRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private OfertaEmpleoService service;

    @BeforeEach
    void setUp() {
        service = new OfertaEmpleoService(ofertas, socios, rubros, users);
    }

    @Test
    void creaOfertaDelSocioNormalizandoCamposOpcionales() {
        Socio socio = socio(10L, "Empresa");
        RubroEmpleo rubro = rubro(20L, "Tecnología", true);
        mockCurrentSocio("socio@example.com", socio);
        when(rubros.findById(20L)).thenReturn(Optional.of(rubro));
        when(ofertas.save(any(OfertaEmpleo.class))).thenAnswer(invocation -> invocation.getArgument(0));

        OfertaResponse response = service.create(
                "socio@example.com",
                false,
                request(null, 20L, "  Desarrollador  ", "  Backend  ", null, null, null));

        assertEquals("Desarrollador", response.titulo());
        assertEquals("Backend", response.descripcion());
        assertEquals(DisponibilidadHoraria.FULL_TIME, response.disponibilidadHoraria());
        assertNull(response.cargo());
        assertNull(response.zona());
        assertNull(response.salario());
        assertNull(response.requisitos());
        assertEquals(1, response.vacantes());
        assertEquals("Empresa", response.socioNombre());
        verify(ofertas).save(any(OfertaEmpleo.class));
    }

    @Test
    void adminCreaOfertaParaSocioActivoYRechazaSocioInvalido() {
        Socio socio = socio(10L, "Empresa");
        RubroEmpleo rubro = rubro(20L, "Tecnología", true);
        when(socios.findById(10L)).thenReturn(Optional.of(socio));
        when(rubros.findById(20L)).thenReturn(Optional.of(rubro));
        when(ofertas.save(any(OfertaEmpleo.class))).thenAnswer(invocation -> invocation.getArgument(0));

        OfertaResponse response = service.create(
                "admin@example.com",
                true,
                request(10L, 20L, "Título", "Descripción", LocalDate.now().plusDays(2), true, DisponibilidadHoraria.PART_TIME));

        assertEquals(DisponibilidadHoraria.PART_TIME, response.disponibilidadHoraria());
        assertEquals(LocalDate.now().plusDays(2), response.fechaCierre());
        assertStatus(HttpStatus.BAD_REQUEST, () -> service.create(
                "admin@example.com", true, request(null, 20L, "Título", "Descripción", null, null, null)));
    }

    @Test
    void listaOfertasDeAdminSocioYPostulanteConFiltroDeRubro() {
        OfertaEmpleo visible = oferta(1L, socio(10L, "Visible"), rubro(20L, "Tecnología", true));
        OfertaEmpleo reservada = oferta(2L, socio(11L, "Oculta"), rubro(21L, "Ventas", true));
        reservada.setMostrarNombreEmpresa(false);
        when(ofertas.findAllByOrderByFechaPublicacionDesc()).thenReturn(List.of(visible));
        when(ofertas.findBySocioIdOrderByFechaPublicacionDesc(10L)).thenReturn(List.of(visible));
        when(ofertas.findByEstadoOrderByFechaPublicacionDesc(EstadoOferta.ACTIVA))
                .thenReturn(List.of(visible, reservada));
        mockCurrentSocio("socio@example.com", visible.getSocio());

        assertEquals(1, service.list("admin@example.com", true).size());
        assertEquals(1, service.list("socio@example.com", false).size());
        assertEquals("Empresa reservada", service.listForApplicants(21L).get(0).socioNombre());
        assertEquals(2, service.listForApplicants(null).size());
    }

    @Test
    void actualizaOfertaPropiaYCambiaSocioCuandoEsAdmin() {
        OfertaEmpleo offer = oferta(1L, socio(10L, "Anterior"), rubro(20L, "Viejo", true));
        Socio nuevo = socio(11L, "Nueva");
        RubroEmpleo rubro = rubro(21L, "Nuevo", true);
        when(ofertas.findById(1L)).thenReturn(Optional.of(offer));
        when(socios.findById(11L)).thenReturn(Optional.of(nuevo));
        when(rubros.findById(21L)).thenReturn(Optional.of(rubro));

        OfertaResponse response = service.update(
                "admin@example.com",
                true,
                1L,
                request(11L, 21L, " Nuevo título ", " Nueva descripción ", null, false, null));

        assertEquals(11L, response.socioId());
        assertEquals("Nuevo título", response.titulo());
        assertEquals("Nueva descripción", response.descripcion());
        assertEquals(21L, response.rubroId());
    }

    @Test
    void protegeOfertaAjenaYOfertasFinalizadas() {
        Socio owner = socio(10L, "Dueña");
        OfertaEmpleo offer = oferta(1L, owner, rubro(20L, "Tecnología", true));
        when(ofertas.findById(1L)).thenReturn(Optional.of(offer));
        mockCurrentSocio("otro@example.com", socio(11L, "Otra"));

        assertStatus(HttpStatus.NOT_FOUND, () -> service.update(
                "otro@example.com", false, 1L, request(null, 20L, "Título", "Descripción", null, null, null)));

        offer.setEstado(EstadoOferta.CERRADA);
        assertStatus(HttpStatus.CONFLICT, () -> service.update(
                "admin@example.com", true, 1L, request(null, 20L, "Título", "Descripción", null, null, null)));
        assertStatus(HttpStatus.CONFLICT, () -> service.setStatus(
                "admin@example.com", true, 1L, EstadoOferta.ACTIVA));
    }

    @Test
    void cambiaEstadoYMarcaComoVencidaSegunFecha() {
        OfertaEmpleo offer = oferta(1L, socio(10L, "Empresa"), rubro(20L, "Tecnología", true));
        when(ofertas.findById(1L)).thenReturn(Optional.of(offer));

        assertEquals(EstadoOferta.PAUSADA,
                service.setStatus("admin@example.com", true, 1L, EstadoOferta.PAUSADA).estado());
        assertStatus(HttpStatus.BAD_REQUEST, () -> service.setStatus(
                "admin@example.com", true, 1L, EstadoOferta.VENCIDA));

        offer.setFechaCierre(LocalDate.now(APP_ZONE).minusDays(1));
        assertEquals(EstadoOferta.VENCIDA,
                service.setStatus("admin@example.com", true, 1L, EstadoOferta.ACTIVA).estado());

        OfertaEmpleo expired = oferta(2L, socio(11L, "Otra"), rubro(21L, "Ventas", true));
        when(ofertas.findByFechaCierreBeforeAndEstadoIn(any(LocalDate.class), any())).thenReturn(List.of(expired));
        service.expirePastOffers();
        assertEquals(EstadoOferta.VENCIDA, expired.getEstado());
    }

    @Test
    void validaDatosRubroYUsuarioSocio() {
        assertStatus(HttpStatus.BAD_REQUEST, () -> service.create(
                "socio@example.com", false, request(null, 20L, " ", "Descripción", null, null, null)));
        assertStatus(HttpStatus.BAD_REQUEST, () -> service.create(
                "socio@example.com", false, request(null, 20L, "Título", " ", null, null, null)));
        assertStatus(HttpStatus.BAD_REQUEST, () -> service.create(
                "socio@example.com", false, request(null, null, "Título", "Descripción", null, null, null)));
        assertStatus(HttpStatus.BAD_REQUEST, () -> service.create(
                "socio@example.com", false, new OfertaRequest(null, 20L, "Título", null, "Descripción",
                        null, null, null, null, null, 0, null, null)));
        assertStatus(HttpStatus.BAD_REQUEST, () -> service.create(
                "socio@example.com", false, request(null, 20L, "Título", "Descripción",
                        LocalDate.now(APP_ZONE).minusDays(1), null, null)));

        mockCurrentSocio("socio@example.com", socio(10L, "Empresa"));
        RubroEmpleo inactive = rubro(20L, "Inactivo", false);
        when(rubros.findById(20L)).thenReturn(Optional.of(inactive));
        assertStatus(HttpStatus.BAD_REQUEST, () -> service.create(
                "socio@example.com", false, request(null, 20L, "Título", "Descripción", null, null, null)));

        when(users.findByEmailIgnoreCase("missing@example.com")).thenReturn(Optional.empty());
        assertStatus(HttpStatus.UNAUTHORIZED, () -> service.list("missing@example.com", false));

        User admin = mock(User.class);
        when(admin.getRole()).thenReturn(Role.ADMIN);
        when(users.findByEmailIgnoreCase("admin@example.com")).thenReturn(Optional.of(admin));
        assertStatus(HttpStatus.FORBIDDEN, () -> service.list("admin@example.com", false));
    }

    @Test
    void listaRubrosActivos() {
        RubroEmpleo technology = rubro(20L, "Tecnología", true);
        RubroEmpleo sales = rubro(21L, "Ventas", true);
        when(rubros.findByActivoTrueOrderByNombreRubroAsc()).thenReturn(List.of(technology, sales));

        assertEquals(
                List.of("Tecnología", "Ventas"),
                service.listOfferSectors().stream().map(OfertaEmpleoService.RubroOptionResponse::nombreRubro).toList());
    }

    private void mockCurrentSocio(String email, Socio socio) {
        User user = socio.getUsuario();
        when(users.findByEmailIgnoreCase(email)).thenReturn(Optional.of(user));
        when(socios.findByUsuarioId(user.getId())).thenReturn(Optional.of(socio));
    }

    private Socio socio(Long id, String name) {
        User user = mock(User.class);
        when(user.getId()).thenReturn(id);
        when(user.getRole()).thenReturn(Role.SOCIO);
        when(user.isActivo()).thenReturn(true);
        Socio socio = mock(Socio.class);
        when(socio.getId()).thenReturn(id);
        when(socio.getRazonSocial()).thenReturn(name);
        when(socio.getUsuario()).thenReturn(user);
        return socio;
    }

    private RubroEmpleo rubro(Long id, String name, boolean active) {
        RubroEmpleo rubro = mock(RubroEmpleo.class);
        when(rubro.getId()).thenReturn(id);
        when(rubro.getNombreRubro()).thenReturn(name);
        when(rubro.isActivo()).thenReturn(active);
        return rubro;
    }

    private OfertaEmpleo oferta(Long id, Socio socio, RubroEmpleo rubro) {
        OfertaEmpleo offer = new OfertaEmpleo(socio, rubro, "Título", "Descripción");
        offer.setDisponibilidadHoraria(DisponibilidadHoraria.FULL_TIME);
        offer.setVacantes(1);
        return offer;
    }

    private OfertaRequest request(
            Long socioId,
            Long rubroId,
            String title,
            String description,
            LocalDate closingDate,
            Boolean showCompany,
            DisponibilidadHoraria availability) {
        return new OfertaRequest(
                socioId, rubroId, title, " ", description, " ", " ", " ",
                TipoContrato.EFECTIVO, availability, 1, closingDate, showCompany);
    }

    private void assertStatus(HttpStatus expected, Runnable action) {
        ResponseStatusException error = assertThrows(ResponseStatusException.class, action::run);
        assertEquals(expected, error.getStatusCode());
    }
}

package uy.ccisj.api.oferta;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
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
import uy.ccisj.api.postulante.Cv;
import uy.ccisj.api.postulante.PerfilLaboral;
import uy.ccisj.api.postulante.Postulante;
import uy.ccisj.api.socio.Socio;
import uy.ccisj.api.socio.SocioRepository;
import uy.ccisj.api.user.Role;
import uy.ccisj.api.user.User;
import uy.ccisj.api.user.UserRepository;

/** Casos borde de ofertas que no cubren los tests existentes del módulo. */
class OfertaEmpleoServiceCoverageTest {
    private final OfertaEmpleoRepository ofertas = mock(OfertaEmpleoRepository.class);
    private final SocioRepository socios = mock(SocioRepository.class);
    private final RubroEmpleoRepository rubros = mock(RubroEmpleoRepository.class);
    private final UserRepository usuarios = mock(UserRepository.class);
    private OfertaEmpleoService service;

    @BeforeEach
    void setUp() {
        service = new OfertaEmpleoService(ofertas, socios, rubros, usuarios);
    }

    @Test
    void validaTituloNuloOLargoDescripcionNulaYVacantesNulas() {
        assertBadRequest(() -> service.create("a@x.test", true, request(null, 1L, null, "Desc", 1, null)),
                "El título es obligatorio");
        assertBadRequest(() -> service.create("a@x.test", true, request(null, 1L, "x".repeat(181), "Desc", 1, null)),
                "El título es obligatorio");
        assertBadRequest(() -> service.create("a@x.test", true, request(null, 1L, "Título", null, 1, null)),
                "La descripción es obligatoria");
        assertBadRequest(() -> service.create("a@x.test", true, request(null, 1L, "Título", "Desc", null, null)),
                "al menos una vacante");
        verify(ofertas, never()).save(any());
    }

    @Test
    void aceptaFechaDeCierreFuturaYTituloDeExactamente180Caracteres() {
        Socio socio = socio(10L, true);
        RubroEmpleo rubro = rubro(20L, true);
        when(socios.findById(10L)).thenReturn(Optional.of(socio));
        when(rubros.findById(20L)).thenReturn(Optional.of(rubro));
        when(ofertas.save(any(OfertaEmpleo.class))).thenAnswer(invocation -> invocation.getArgument(0));

        OfertaResponse response = service.create("admin@x.test", true,
                request(10L, 20L, "t".repeat(180), "Desc", 3, LocalDate.now().plusDays(10)));

        assertThat(response.titulo()).hasSize(180);
        assertThat(response.vacantes()).isEqualTo(3);
        assertThat(response.fechaCierre()).isEqualTo(LocalDate.now().plusDays(10));
    }

    @Test
    void adminRechazaSocioInexistenteOInactivo() {
        Socio inactivo = socio(10L, false);
        when(socios.findById(10L)).thenReturn(Optional.of(inactivo));
        when(socios.findById(11L)).thenReturn(Optional.empty());

        assertBadRequest(() -> service.create("admin@x.test", true, request(10L, 20L, "Título", "Desc", 1, null)),
                "no está activo");
        assertBadRequest(() -> service.create("admin@x.test", true, request(11L, 20L, "Título", "Desc", 1, null)),
                "no está activo");
    }

    @Test
    void socioInactivoNoPuedeOperarSusOfertas() {
        Socio socio = socio(10L, false);
        User usuario = socio.getUsuario();
        when(usuarios.findByEmailIgnoreCase("socio@x.test")).thenReturn(Optional.of(usuario));
        when(socios.findByUsuarioId(10L)).thenReturn(Optional.of(socio));

        assertThatThrownBy(() -> service.list("socio@x.test", false))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Socio no encontrado o inactivo");
    }

    @Test
    void actualizarOfertaRechazaInexistenteVencidaYRubroInvalido() {
        when(ofertas.findById(404L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.update("admin@x.test", true, 404L, request(null, 20L, "Título", "Desc", 1, null)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Oferta no encontrada");

        OfertaEmpleo oferta = new OfertaEmpleo(socio(10L, true), rubro(20L, true), "Título", "Desc");
        when(ofertas.findById(1L)).thenReturn(Optional.of(oferta));
        RubroEmpleo rubroInactivo = rubro(21L, false);
        when(rubros.findById(21L)).thenReturn(Optional.of(rubroInactivo));
        assertBadRequest(() -> service.update("admin@x.test", true, 1L, request(null, 21L, "Título", "Desc", 1, null)),
                "Rubro inválido");
        when(rubros.findById(22L)).thenReturn(Optional.empty());
        assertBadRequest(() -> service.update("admin@x.test", true, 1L, request(null, 22L, "Título", "Desc", 1, null)),
                "Rubro inválido");

        oferta.setEstado(EstadoOferta.VENCIDA);
        assertThatThrownBy(() -> service.update("admin@x.test", true, 1L, request(null, 20L, "Título", "Desc", 1, null)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("cerrada o vencida");
    }

    @Test
    void adminActualizaSinCambiarSocioCuandoNoSeIndica() {
        Socio original = socio(10L, true);
        OfertaEmpleo oferta = new OfertaEmpleo(original, rubro(20L, true), "Título", "Desc");
        when(ofertas.findById(1L)).thenReturn(Optional.of(oferta));
        RubroEmpleo mismoRubro = rubro(20L, true);
        when(rubros.findById(20L)).thenReturn(Optional.of(mismoRubro));

        OfertaResponse response = service.update("admin@x.test", true, 1L,
                request(null, 20L, "Nuevo", "Nueva desc", 2, null));

        assertThat(response.socioId()).isEqualTo(10L);
        assertThat(oferta.getSocio()).isSameAs(original);
        assertThat(response.titulo()).isEqualTo("Nuevo");
        verify(socios, never()).findById(any());
    }

    @Test
    void cambiaEstadoACerradaConFechaFuturaYRechazaOfertaVencida() {
        OfertaEmpleo oferta = new OfertaEmpleo(socio(10L, true), rubro(20L, true), "Título", "Desc");
        oferta.setFechaCierre(LocalDate.now().plusDays(5));
        when(ofertas.findById(1L)).thenReturn(Optional.of(oferta));

        assertThat(service.setStatus("admin@x.test", true, 1L, EstadoOferta.ACTIVA).estado())
                .isEqualTo(EstadoOferta.ACTIVA);
        assertThat(service.setStatus("admin@x.test", true, 1L, EstadoOferta.CERRADA).estado())
                .isEqualTo(EstadoOferta.CERRADA);

        OfertaEmpleo vencida = new OfertaEmpleo(socio(10L, true), rubro(20L, true), "Título", "Desc");
        vencida.setEstado(EstadoOferta.VENCIDA);
        when(ofertas.findById(2L)).thenReturn(Optional.of(vencida));
        assertThatThrownBy(() -> service.setStatus("admin@x.test", true, 2L, EstadoOferta.ACTIVA))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("ya está cerrada");
    }

    @Test
    void schedulerDelegaLaExpiracionDeOfertasVencidas() {
        OfertaEmpleo activa = new OfertaEmpleo(socio(10L, true), rubro(20L, true), "Título", "Desc");
        when(ofertas.findByFechaCierreBeforeAndEstadoIn(any(LocalDate.class), any())).thenReturn(List.of(activa));

        new OfertaEmpleoExpirationScheduler(service).expirePastOffers();

        assertThat(activa.getEstado()).isEqualTo(EstadoOferta.VENCIDA);
        verify(ofertas).findByFechaCierreBeforeAndEstadoIn(LocalDate.now(java.time.ZoneId.of("America/Montevideo")),
                List.of(EstadoOferta.ACTIVA, EstadoOferta.PAUSADA));
    }

    @Test
    void postulacionExponeYModificaSusAsociaciones() {
        Postulante postulante = mock(Postulante.class);
        Postulante otro = mock(Postulante.class);
        OfertaEmpleo oferta = mock(OfertaEmpleo.class);
        OfertaEmpleo otraOferta = mock(OfertaEmpleo.class);
        PerfilLaboral perfil = mock(PerfilLaboral.class);
        Cv cv = mock(Cv.class);

        Postulacion postulacion = new Postulacion(postulante, oferta);
        assertThat(postulacion.getPostulante()).isSameAs(postulante);
        assertThat(postulacion.getOferta()).isSameAs(oferta);
        assertThat(postulacion.getEstado()).isEqualTo(EstadoPostulacion.RECIBIDA);
        assertThat(postulacion.isRubroPrincipal()).isFalse();
        assertThat(postulacion.getId()).isNull();
        assertThat(postulacion.getFecha()).isNull();

        postulacion.setPostulante(otro);
        postulacion.setOferta(otraOferta);
        postulacion.setPerfilLaboral(perfil);
        postulacion.setCv(cv);
        postulacion.setRubroPrincipal(true);

        assertThat(postulacion.getPostulante()).isSameAs(otro);
        assertThat(postulacion.getOferta()).isSameAs(otraOferta);
        assertThat(postulacion.getPerfilLaboral()).isSameAs(perfil);
        assertThat(postulacion.getCv()).isSameAs(cv);
        assertThat(postulacion.isRubroPrincipal()).isTrue();
        assertThat(new Postulacion().getEstado()).isEqualTo(EstadoPostulacion.RECIBIDA);
    }

    private static Socio socio(Long id, boolean activo) {
        User usuario = mock(User.class);
        when(usuario.getId()).thenReturn(id);
        when(usuario.getRole()).thenReturn(Role.SOCIO);
        when(usuario.isActivo()).thenReturn(activo);
        Socio socio = mock(Socio.class);
        when(socio.getId()).thenReturn(id);
        when(socio.getRazonSocial()).thenReturn("Empresa " + id);
        when(socio.getUsuario()).thenReturn(usuario);
        return socio;
    }

    private static RubroEmpleo rubro(Long id, boolean activo) {
        RubroEmpleo rubro = mock(RubroEmpleo.class);
        when(rubro.getId()).thenReturn(id);
        when(rubro.getNombreRubro()).thenReturn("Rubro " + id);
        when(rubro.isActivo()).thenReturn(activo);
        return rubro;
    }

    private static OfertaRequest request(Long socioId, Long rubroId, String titulo, String descripcion,
            Integer vacantes, LocalDate cierre) {
        return new OfertaRequest(socioId, rubroId, titulo, "Cargo", descripcion, "Req", "Zona", "$ 1",
                TipoContrato.EFECTIVO, DisponibilidadHoraria.PART_TIME, vacantes, cierre, null);
    }

    private static void assertBadRequest(Runnable accion, String mensaje) {
        assertThatThrownBy(accion::run)
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining(mensaje)
                .satisfies(error -> assertThat(((ResponseStatusException) error).getStatusCode())
                        .isEqualTo(HttpStatus.BAD_REQUEST));
    }
}

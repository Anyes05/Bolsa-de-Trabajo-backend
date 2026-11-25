package uy.ccisj.api.socio;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;
import uy.ccisj.api.catalogo.RubroEmpleo;
import uy.ccisj.api.catalogo.RubroEmpleoRepository;
import uy.ccisj.api.domain.EstadoMorosidad;
import uy.ccisj.api.mail.MailNotificationService;
import uy.ccisj.api.mail.MailTemplate;
import uy.ccisj.api.socio.dto.SocioCreateDTO;
import uy.ccisj.api.socio.dto.SocioPerfilUpdateDTO;
import uy.ccisj.api.socio.dto.SocioResponseDTO;
import uy.ccisj.api.socio.dto.SocioUpdateDTO;
import uy.ccisj.api.socio.dto.TipoIdentificadorSocio;
import uy.ccisj.api.user.Role;
import uy.ccisj.api.user.User;
import uy.ccisj.api.user.UserRepository;

/**
 * Cobertura unitaria de SocioService: altas, ediciones, suscripción, credenciales y perfil propio.
 */
class SocioServiceCoverageTest {
    private static final String WEB = "https://web.example.test";
    private static final String LOCALIDAD = "Libertad";
    private static final String RUT = "123456789012";

    private final SocioRepository socios = mock(SocioRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final DireccionRepository direcciones = mock(DireccionRepository.class);
    private final RubroEmpleoRepository rubros = mock(RubroEmpleoRepository.class);
    private final PasswordEncoder encoder = mock(PasswordEncoder.class);
    private final CajaService caja = mock(CajaService.class);
    private final MailNotificationService mail = mock(MailNotificationService.class);
    private final SocioSubscriptionHistory history = mock(SocioSubscriptionHistory.class);
    private final SocioService service = new SocioService(
            socios, users, direcciones, rubros, encoder, caja, mail, WEB, history);

    private RubroEmpleo comercio;
    private RubroEmpleo industria;

    @BeforeEach
    void setUp() {
        when(encoder.encode(anyString())).thenAnswer(inv -> "hash:" + inv.getArgument(0));
        when(direcciones.save(any(Direccion.class))).thenAnswer(inv -> inv.getArgument(0));
        when(socios.save(any(Socio.class))).thenAnswer(inv -> {
            Socio socio = inv.getArgument(0);
            if (socio.getId() == null) ReflectionTestUtils.setField(socio, "id", 500L);
            return socio;
        });
        comercio = rubro(1L, "Comercio", true);
        industria = rubro(2L, "Industria", true);
        when(rubros.findById(1L)).thenReturn(Optional.of(comercio));
        when(rubros.findById(2L)).thenReturn(Optional.of(industria));
        when(rubros.findById(3L)).thenReturn(Optional.of(rubro(3L, "Rubro dado de baja", false)));
        when(rubros.findById(99L)).thenReturn(Optional.empty());
        when(history.today()).thenReturn(LocalDate.of(2026, 5, 10));
    }

    // ---------- helpers ----------

    private static RubroEmpleo rubro(Long id, String nombre, boolean activo) {
        RubroEmpleo rubro = new RubroEmpleo(nombre);
        ReflectionTestUtils.setField(rubro, "id", id);
        rubro.setActivo(activo);
        return rubro;
    }

    private static User user(Long id, String email, Role role) {
        User user = new User(email, null, "hash-anterior", role);
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private Socio socio(Long id, User user, String razonSocial) {
        Socio socio = new Socio(user, razonSocial);
        ReflectionTestUtils.setField(socio, "id", id);
        when(socios.findById(id)).thenReturn(Optional.of(socio));
        return socio;
    }

    private Socio socioLogueado(String email) {
        User user = user(10L, email, Role.SOCIO);
        Socio socio = socio(7L, user, "Empresa logueada");
        when(users.findByEmailIgnoreCase(email)).thenReturn(Optional.of(user));
        when(socios.findByUsuarioId(10L)).thenReturn(Optional.of(socio));
        return socio;
    }

    private static HttpStatus status(Executable executable) {
        return HttpStatus.valueOf(assertThrows(ResponseStatusException.class, executable).getStatusCode().value());
    }

    private static String motivo(Executable executable) {
        return assertThrows(ResponseStatusException.class, executable).getReason();
    }

    private static SocioCreateDTO alta(TipoIdentificadorSocio tipo, String identificador, String email, String rut,
            String emailContacto, LocalDate fechaAlta) {
        return new SocioCreateDTO(tipo, identificador, email, "  Empresa Nueva  ", rut, " 099 123 ",
                emailContacto, "  18 de Julio ", "  ", LOCALIDAD, 1L, true, fechaAlta, LocalDate.of(2020, 3, 4));
    }

    private static SocioUpdateDTO edicion(String bps, String emailUsuario, String rut, Long rubroId,
            LocalDate fechaAlta, LocalDate fechaAniversario) {
        return new SocioUpdateDTO(bps, emailUsuario, " Nueva Razón ", rut, " 098 765 ", " Contacto@Empresa.Test ",
                " Calle Nueva ", " 55 ", " " + LOCALIDAD + " ", rubroId, false, fechaAlta, fechaAniversario);
    }

    private MailTemplate.Content mailEnviado(String destinatario) {
        ArgumentCaptor<MailTemplate.Content> content = ArgumentCaptor.forClass(MailTemplate.Content.class);
        verify(mail).send(eq(destinatario), eq("CCISJ — Tus datos para ingresar a la Bolsa de Trabajo"), content.capture());
        return content.getValue();
    }

    // ---------- consultas ----------

    @Test
    void getSocioByIdDevuelveDatosCompletosOFallaConNotFound() {
        User user = user(10L, "cuenta@empresa.test", Role.SOCIO);
        user.setCedula("12345678");
        user.setBps("123456789");
        Socio socio = socio(7L, user, "Empresa");
        socio.setDireccion(new Direccion("Calle", "12", LOCALIDAD));
        socio.setRubro(comercio);
        socio.setCamposFicticios("telefono, rut");

        SocioResponseDTO dto = service.getSocioById(7L);
        assertEquals(7L, dto.id());
        assertEquals(TipoIdentificadorSocio.CEDULA, dto.tipoIdentificador());
        assertEquals("Calle", dto.calle());
        assertEquals("12", dto.numero());
        assertEquals(LOCALIDAD, dto.localidad());
        assertEquals(1L, dto.rubroId());
        assertEquals("Comercio", dto.nombreRubro());
        assertEquals(List.of("telefono", "rut"), dto.camposFicticios());
        assertEquals("cuenta@empresa.test", dto.emailUsuario());
        assertTrue(dto.activo());
        assertNull(dto.passwordProvisional());
        assertNull(dto.credencialesEnviadas());

        when(socios.findById(8L)).thenReturn(Optional.empty());
        assertEquals(HttpStatus.NOT_FOUND, status(() -> service.getSocioById(8L)));
    }

    @Test
    void resuelveTipoIdentificadorSegunBpsOCedula() {
        User conBps = user(11L, "bps@empresa.test", Role.SOCIO);
        conBps.setBps("123456789");
        conBps.setCedula("   ");
        socio(1L, conBps, "Con BPS");
        User sinIdentificador = user(12L, "nada@empresa.test", Role.SOCIO);
        sinIdentificador.setBps("  ");
        socio(2L, sinIdentificador, "Sin identificador");
        Socio sinUsuario = socio(3L, null, "Sin usuario");

        assertEquals(TipoIdentificadorSocio.BPS, service.getSocioById(1L).tipoIdentificador());
        assertNull(service.getSocioById(2L).tipoIdentificador());
        SocioResponseDTO dto = service.getSocioById(3L);
        assertNull(dto.tipoIdentificador());
        assertNull(dto.bps());
        assertNull(dto.cedula());
        assertNull(dto.emailUsuario());
        assertNull(dto.fechaBaja());
        assertNull(dto.fotoUrl());
        assertNull(dto.calle());
        assertNull(dto.rubroId());
        assertTrue(dto.activo(), "un socio sin cuenta se considera activo");
        assertEquals(sinUsuario.getEstadoMorosidad(), dto.estadoMorosidad());
    }

    @Test
    void listRubrosMapeaIdYNombre() {
        when(rubros.findByActivoTrueOrderByNombreRubroAsc()).thenReturn(List.of(comercio, industria));
        var lista = service.listRubros();
        assertEquals(2, lista.size());
        assertEquals(1L, lista.get(0).id());
        assertEquals("Comercio", lista.get(0).nombreRubro());
        assertEquals("Industria", lista.get(1).nombreRubro());
    }

    @Test
    void directorioSoloParaDirectivosYResuelveEmailRubroYEstado() {
        Socio viewer = socioLogueado("directivo@empresa.test");
        assertEquals(HttpStatus.FORBIDDEN, status(() -> service.listDirectorio("directivo@empresa.test")));
        viewer.setEsDirectivo(true);

        Socio conContacto = new Socio(user(20L, "cuenta1@empresa.test", Role.SOCIO), "A con contacto");
        conContacto.setEmailContacto("  contacto@a.test ");
        conContacto.setRubro(comercio);
        conContacto.setTelefono("099");
        User inactivo = user(21L, "cuenta2@empresa.test", Role.SOCIO);
        inactivo.setActivo(false);
        Socio sinContacto = new Socio(inactivo, "B sin contacto");
        sinContacto.setEmailContacto("   ");
        sinContacto.setEstadoMorosidad(EstadoMorosidad.MOROSO);
        Socio sinUsuario = new Socio(null, "C sin usuario");
        sinUsuario.setEstadoMorosidad(EstadoMorosidad.MOROSO);
        when(socios.findAllByOrderByRazonSocialAsc()).thenReturn(List.of(viewer, conContacto, sinContacto, sinUsuario));

        var directorio = service.listDirectorio("directivo@empresa.test");
        assertEquals(4, directorio.size());
        assertTrue(directorio.get(0).esDirectivo());
        assertEquals("contacto@a.test", directorio.get(1).email());
        assertEquals("Comercio", directorio.get(1).rubro());
        assertEquals("099", directorio.get(1).telefono());
        assertEquals(EstadoMorosidad.AL_DIA, directorio.get(1).estadoMorosidad());
        assertEquals("cuenta2@empresa.test", directorio.get(2).email());
        assertEquals("Sin rubro", directorio.get(2).rubro());
        assertEquals(EstadoMorosidad.INACTIVO, directorio.get(2).estadoMorosidad());
        assertNull(directorio.get(3).email());
        assertEquals(EstadoMorosidad.MOROSO, directorio.get(3).estadoMorosidad());

        // Búsqueda, estado y paginación en servidor.
        var morosos = service.pageDirectorio("directivo@empresa.test",
                uy.ccisj.api.common.ListQuery.of(null, 0, 20), EstadoMorosidad.MOROSO);
        assertEquals(1, morosos.totalElements());
        assertEquals("C sin usuario", morosos.content().getFirst().razonSocial());
        var porEmail = service.pageDirectorio("directivo@empresa.test",
                uy.ccisj.api.common.ListQuery.of("CONTACTO@A", 0, 20), null);
        assertEquals(1, porEmail.totalElements());
        var pagina = service.pageDirectorio("directivo@empresa.test", uy.ccisj.api.common.ListQuery.of(null, 1, 3), null);
        assertEquals(1, pagina.content().size());
        assertEquals(4, pagina.totalElements());
        assertEquals(2, pagina.totalPages());
    }

    @Test
    void directorioPaginadoTambienExigeDirectivo() {
        socioLogueado("estandar@empresa.test");
        assertEquals(HttpStatus.FORBIDDEN, status(() -> service.pageDirectorio("estandar@empresa.test",
                uy.ccisj.api.common.ListQuery.of(null, 0, 20), null)));
    }

    @Test
    void listadoDeSociosFiltraIncompletosYBuscaEnServidor() {
        Socio completo = new Socio(user(30L, "completa@empresa.test", Role.SOCIO), "Completa SA");
        completo.setAccesoHabilitado(true);
        completo.setRubro(comercio);
        Socio sinAcceso = new Socio(user(31L, "pendiente@empresa.test", Role.SOCIO), "Pendiente SRL");
        sinAcceso.setAccesoHabilitado(false);
        when(socios.findAllByOrderByRazonSocialAsc()).thenReturn(List.of(completo, sinAcceso));

        var todos = service.pageSocios(uy.ccisj.api.common.ListQuery.of(null, 0, 20), false);
        assertEquals(2, todos.totalElements());
        var incompletos = service.pageSocios(uy.ccisj.api.common.ListQuery.of(null, 0, 20), true);
        assertEquals(List.of("Pendiente SRL"), incompletos.content().stream().map(SocioResponseDTO::razonSocial).toList());
        var porRubro = service.pageSocios(uy.ccisj.api.common.ListQuery.of("comercio", 0, 20), false);
        assertEquals(List.of("Completa SA"), porRubro.content().stream().map(SocioResponseDTO::razonSocial).toList());
        var porEmail = service.pageSocios(uy.ccisj.api.common.ListQuery.of("PENDIENTE@", 0, 20), false);
        assertEquals(1, porEmail.totalElements());
        var pagina = service.pageSocios(uy.ccisj.api.common.ListQuery.of(null, 1, 1), false);
        assertEquals(List.of("Pendiente SRL"), pagina.content().stream().map(SocioResponseDTO::razonSocial).toList());
        assertEquals(2, pagina.totalPages());
    }

    @Test
    void requiereCuentaDeSocioActivaParaElPerfilPropio() {
        when(users.findByEmailIgnoreCase("nadie@x.test")).thenReturn(Optional.empty());
        assertEquals("Se requiere una cuenta de socio activa", motivo(() -> service.getMiPerfil("nadie@x.test")));

        User inactivo = user(30L, "inactivo@x.test", Role.SOCIO);
        inactivo.setActivo(false);
        when(users.findByEmailIgnoreCase("inactivo@x.test")).thenReturn(Optional.of(inactivo));
        assertEquals(HttpStatus.FORBIDDEN, status(() -> service.getMiPerfil("inactivo@x.test")));

        User postulante = user(31L, "postulante@x.test", Role.POSTULANTE);
        when(users.findByEmailIgnoreCase("postulante@x.test")).thenReturn(Optional.of(postulante));
        assertEquals(HttpStatus.FORBIDDEN, status(() -> service.getMiPerfil("postulante@x.test")));

        User huerfano = user(32L, "huerfano@x.test", Role.SOCIO);
        when(users.findByEmailIgnoreCase("huerfano@x.test")).thenReturn(Optional.of(huerfano));
        when(socios.findByUsuarioId(32L)).thenReturn(Optional.empty());
        assertEquals("Socio no encontrado", motivo(() -> service.getMiPerfil("huerfano@x.test")));
    }

    // ---------- perfil propio ----------

    @Test
    void updateMiPerfilCreaDireccionCuandoNoExiste() {
        Socio socio = socioLogueado("yo@empresa.test");
        var dto = new SocioPerfilUpdateDTO("  Mi Empresa  ", "   ", "  Contacto@MiEmpresa.Test ", " Rivera ", " 100 ",
                " " + LOCALIDAD + " ", 2L, LocalDate.of(2019, 1, 2));

        SocioResponseDTO respuesta = service.updateMiPerfil("yo@empresa.test", dto);

        assertEquals("Mi Empresa", respuesta.razonSocial());
        assertNull(respuesta.telefono());
        assertEquals("contacto@miempresa.test", respuesta.emailContacto());
        assertEquals("Industria", respuesta.nombreRubro());
        assertEquals(LocalDate.of(2019, 1, 2), respuesta.fechaAniversario());
        assertEquals("Rivera", respuesta.calle());
        assertEquals("100", respuesta.numero());
        assertEquals(LOCALIDAD, respuesta.localidad());
        assertNotNull(socio.getDireccion());
        verify(direcciones).save(socio.getDireccion());
        verify(socios).save(socio);
    }

    @Test
    void updateMiPerfilActualizaDireccionExistenteYValidaRubroYLocalidad() {
        Socio socio = socioLogueado("yo@empresa.test");
        Direccion existente = new Direccion("Vieja", "1", "Rodríguez");
        socio.setDireccion(existente);
        var dto = new SocioPerfilUpdateDTO("Mi Empresa", " 091 ", null, " Nueva ", null, "Capurro", 1L, null);

        SocioResponseDTO respuesta = service.updateMiPerfil("yo@empresa.test", dto);
        assertSame(existente, socio.getDireccion());
        assertEquals("Nueva", existente.getCalle());
        assertNull(existente.getNumero());
        assertEquals("Capurro", existente.getLocalidad());
        assertEquals("091", respuesta.telefono());
        assertNull(respuesta.emailContacto());

        var localidadInvalida = new SocioPerfilUpdateDTO("Mi Empresa", "1", null, "Calle", null, "Montevideo", 1L, null);
        assertEquals("La localidad debe ser una de las localidades de San José precargadas",
                motivo(() -> service.updateMiPerfil("yo@empresa.test", localidadInvalida)));
        var localidadNula = new SocioPerfilUpdateDTO("Mi Empresa", "1", null, "Calle", null, null, 1L, null);
        assertEquals(HttpStatus.BAD_REQUEST, status(() -> service.updateMiPerfil("yo@empresa.test", localidadNula)));
        var rubroInactivo = new SocioPerfilUpdateDTO("Mi Empresa", "1", null, "Calle", null, LOCALIDAD, 3L, null);
        assertEquals("El rubro no es válido", motivo(() -> service.updateMiPerfil("yo@empresa.test", rubroInactivo)));
        var rubroInexistente = new SocioPerfilUpdateDTO("Mi Empresa", "1", null, "Calle", null, LOCALIDAD, 99L, null);
        assertEquals(HttpStatus.BAD_REQUEST, status(() -> service.updateMiPerfil("yo@empresa.test", rubroInexistente)));
    }

    @Test
    void updateSilenciarCorreosExigeValorYLoGuarda() {
        Socio socio = socioLogueado("yo@empresa.test");
        assertEquals(HttpStatus.BAD_REQUEST, status(() -> service.updateSilenciarCorreos("yo@empresa.test", null)));
        verify(socios, never()).save(any());

        assertTrue(service.updateSilenciarCorreos("yo@empresa.test", true).silenciarCorreos());
        assertTrue(socio.isSilenciarCorreos());
        assertFalse(service.updateSilenciarCorreos("yo@empresa.test", false).silenciarCorreos());
    }

    // ---------- alta ----------

    @Test
    void createSocioValidaIdentificadorYRut() {
        assertEquals("El número de cédula es obligatorio", motivo(() -> service.createSocio(
                alta(TipoIdentificadorSocio.CEDULA, "abc", "a@b.test", RUT, null, null))));
        assertEquals("El número de BPS es obligatorio", motivo(() -> service.createSocio(
                alta(TipoIdentificadorSocio.BPS, null, "a@b.test", RUT, null, null))));
        assertEquals("El número de BPS debe tener al menos 9 dígitos", motivo(() -> service.createSocio(
                alta(TipoIdentificadorSocio.BPS, "12345678", "a@b.test", RUT, null, null))));
        assertEquals("El RUT o la cédula es obligatorio", motivo(() -> service.createSocio(
                alta(TipoIdentificadorSocio.CEDULA, "12345678", "a@b.test", " - ", null, null))));
        assertEquals("Debe ser un RUT de 12 dígitos o una cédula de 8 dígitos", motivo(() -> service.createSocio(
                alta(TipoIdentificadorSocio.CEDULA, "12345678", "a@b.test", "1234567", null, null))));
        verify(users, never()).save(any());
    }

    @Test
    void createSocioRechazaDuplicados() {
        when(users.existsByBps("123456789")).thenReturn(true);
        assertEquals("Ya existe un socio con ese BPS", motivo(() -> service.createSocio(
                alta(TipoIdentificadorSocio.BPS, "1.234.567-89", "a@b.test", RUT, null, null))));
        assertEquals("Ese número ya está registrado como BPS de otro socio", motivo(() -> service.createSocio(
                alta(TipoIdentificadorSocio.CEDULA, "123456789", "a@b.test", RUT, null, null))));

        when(users.existsByBps("123456789")).thenReturn(false);
        when(users.existsByCedula("123456789")).thenReturn(true);
        assertEquals("Ese número ya está registrado como cédula de otro socio", motivo(() -> service.createSocio(
                alta(TipoIdentificadorSocio.BPS, "123456789", "a@b.test", RUT, null, null))));
        assertEquals("Ya existe un socio con esa cédula", motivo(() -> service.createSocio(
                alta(TipoIdentificadorSocio.CEDULA, "123456789", "a@b.test", RUT, null, null))));

        when(users.existsByEmailIgnoreCase("repetido@b.test")).thenReturn(true);
        assertEquals("Ya existe una cuenta con ese correo", motivo(() -> service.createSocio(
                alta(TipoIdentificadorSocio.CEDULA, "12345678", " repetido@b.test ", RUT, null, null))));

        when(socios.existsByRutIgnoreCase("12345678")).thenReturn(true);
        assertEquals(HttpStatus.CONFLICT, status(() -> service.createSocio(
                alta(TipoIdentificadorSocio.CEDULA, "12345678", "otro@b.test", "1234.5678", null, null))));
        verify(users, never()).save(any());
    }

    @Test
    void createSocioConBpsGuardaUsuarioDireccionHistorialYEnviaCredenciales() {
        when(mail.isConfigured()).thenReturn(true);
        LocalDate fechaAlta = LocalDate.of(2024, 2, 1);

        SocioResponseDTO dto = service.createSocio(
                alta(TipoIdentificadorSocio.BPS, "123.456.789", " Cuenta@Empresa.Test ", "12.345.678.901-2",
                        " Ventas@Empresa.Test ", fechaAlta));

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(users).save(userCaptor.capture());
        User guardado = userCaptor.getValue();
        assertEquals("cuenta@empresa.test", guardado.getEmail());
        assertEquals("123456789", guardado.getBps());
        assertNull(guardado.getCedula());
        assertEquals(Role.SOCIO, guardado.getRole());
        assertTrue(guardado.isDebeCambiarPassword());
        assertTrue(guardado.getPasswordProvisionalHasta().isAfter(OffsetDateTime.now().plusHours(23)));
        assertEquals("hash:" + dto.passwordProvisional(), guardado.getPasswordHash());

        assertEquals(500L, dto.id());
        assertEquals("Empresa Nueva", dto.razonSocial());
        assertEquals("123456789012", dto.rut());
        assertEquals("099 123", dto.telefono());
        assertEquals("ventas@empresa.test", dto.emailContacto());
        assertEquals("18 de Julio", dto.calle());
        assertNull(dto.numero());
        assertEquals(TipoIdentificadorSocio.BPS, dto.tipoIdentificador());
        assertEquals(EstadoMorosidad.AL_DIA, dto.estadoMorosidad());
        assertTrue(dto.esDirectivo());
        assertEquals(fechaAlta, dto.fechaAlta());
        assertEquals(LocalDate.of(2020, 3, 4), dto.fechaAniversario());
        assertTrue(dto.credencialesEnviadas());
        assertEquals("Credenciales enviadas automáticamente a ventas@empresa.test", dto.credencialesEnvioMensaje());
        verify(history).record(500L, "ALTA", fechaAlta);

        MailTemplate.Content content = mailEnviado("ventas@empresa.test");
        assertTrue(content.plain().contains("Hola, Empresa Nueva:"));
        assertTrue(content.plain().contains("Usuario (BPS): 123456789"));
        assertTrue(content.plain().contains("Contraseña provisional: " + dto.passwordProvisional()));
        assertTrue(content.plain().contains("Sitio web: " + WEB));
    }

    @Test
    void createSocioConCedulaSinFechaAltaUsaHoyYAvisaSiElCorreoNoEstaConfigurado() {
        when(mail.isConfigured()).thenReturn(false);

        SocioResponseDTO dto = service.createSocio(
                alta(TipoIdentificadorSocio.CEDULA, "1.234.567-8", "cuenta@empresa.test", "1234567-8", null, null));

        assertEquals(TipoIdentificadorSocio.CEDULA, dto.tipoIdentificador());
        assertEquals("12345678", dto.cedula());
        assertNull(dto.bps());
        assertEquals(LocalDate.now(), dto.fechaAlta());
        assertFalse(dto.credencialesEnviadas());
        assertEquals("No se envió el mail: el servicio de correo no está configurado. Podés reenviarlo después.",
                dto.credencialesEnvioMensaje());
        verify(mail, never()).send(anyString(), anyString(), any(MailTemplate.Content.class));
    }

    @Test
    void createSocioSinNingunEmailNoIntentaEnviar() {
        when(mail.isConfigured()).thenReturn(true);

        SocioResponseDTO dto = service.createSocio(
                alta(TipoIdentificadorSocio.CEDULA, "12345678", "   ", RUT, "  ", null));

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(users).save(userCaptor.capture());
        assertNull(userCaptor.getValue().getEmail());
        verify(users, never()).existsByEmailIgnoreCase(any());
        assertFalse(dto.credencialesEnviadas());
        assertEquals("No se envió el mail: el socio no tiene email de contacto ni de cuenta.",
                dto.credencialesEnvioMensaje());
        verify(mail, never()).isConfigured();
    }

    @Test
    void createSocioNoSeAbortaSiFallaElEnvioYResumeElError() {
        when(mail.isConfigured()).thenReturn(true);
        doThrow(new IllegalStateException()).when(mail)
                .send(anyString(), anyString(), any(MailTemplate.Content.class));

        SocioResponseDTO sinMensaje = service.createSocio(
                alta(TipoIdentificadorSocio.CEDULA, "12345678", "cuenta@empresa.test", RUT, null, null));
        assertFalse(sinMensaje.credencialesEnviadas());
        assertEquals("El socio se creó, pero no se pudo enviar el mail (IllegalStateException). "
                + "Podés reenviarlo desde esta pantalla.", sinMensaje.credencialesEnvioMensaje());
        assertNotNull(sinMensaje.passwordProvisional());

        doThrow(new IllegalStateException("   ")).when(mail)
                .send(anyString(), anyString(), any(MailTemplate.Content.class));
        assertTrue(service.createSocio(
                alta(TipoIdentificadorSocio.CEDULA, "12345678", "cuenta@empresa.test", RUT, null, null))
                .credencialesEnvioMensaje().contains("(IllegalStateException)"));

        doThrow(new IllegalStateException("SMTP caído")).when(mail)
                .send(anyString(), anyString(), any(MailTemplate.Content.class));
        assertTrue(service.createSocio(
                alta(TipoIdentificadorSocio.CEDULA, "12345678", "cuenta@empresa.test", RUT, null, null))
                .credencialesEnvioMensaje().contains("(SMTP caído)"));

        String largo = "x".repeat(200);
        doThrow(new IllegalStateException(largo)).when(mail)
                .send(anyString(), anyString(), any(MailTemplate.Content.class));
        String mensaje = service.createSocio(
                alta(TipoIdentificadorSocio.CEDULA, "12345678", "cuenta@empresa.test", RUT, null, null))
                .credencialesEnvioMensaje();
        assertTrue(mensaje.contains("(" + "x".repeat(180) + "…)"));
        assertFalse(mensaje.contains("x".repeat(181)));
    }

    // ---------- edición por administración ----------

    @Test
    void updateSocioRechazaRutDeOtroSocio() {
        socio(7L, user(10L, "c@e.test", Role.SOCIO), "Empresa");
        when(socios.existsByRutIgnoreCaseAndIdNot(RUT, 7L)).thenReturn(true);
        assertEquals("Ya existe un socio con ese RUT o cédula", motivo(() -> service.updateSocio(7L,
                edicion(null, null, RUT, 1L, null, null))));
        verify(history).lock(7L);
        verify(socios, never()).save(any());
    }

    @Test
    void updateSocioConDatosRealesLimpiaTodosLosCamposFicticios() {
        User user = user(10L, "ficticio@incompleto.ccisj.local", Role.SOCIO);
        user.setBps("000000000");
        Socio socio = socio(7L, user, "Vieja Razón");
        socio.setRut("000000000000");
        socio.setTelefono("000");
        socio.setEmailContacto("viejo@empresa.test");
        socio.setRubro(comercio);
        socio.setFechaAlta(LocalDate.of(2000, 1, 1));
        socio.setFechaAniversario(LocalDate.of(2000, 1, 1));
        socio.setDireccion(new Direccion("Calle Vieja", null, "Rodríguez"));
        socio.setCamposFicticios(
                "bps,rut,telefono,emailContacto,emailUsuario,calle,numero,localidad,rubro,fechaAlta,fechaAniversario");
        socio.setEstadoMorosidad(EstadoMorosidad.MOROSO);

        SocioResponseDTO dto = service.updateSocio(7L, edicion(" 123.456.789 ", " Real@Empresa.Test ",
                RUT, 2L, LocalDate.of(2010, 5, 5), LocalDate.of(2011, 6, 6)));

        assertNull(socio.getCamposFicticios());
        assertEquals(List.of(), dto.camposFicticios());
        assertEquals("Nueva Razón", dto.razonSocial());
        assertEquals(RUT, dto.rut());
        assertEquals("098 765", dto.telefono());
        assertEquals("contacto@empresa.test", dto.emailContacto());
        assertEquals("Industria", dto.nombreRubro());
        assertFalse(dto.esDirectivo());
        assertEquals(LocalDate.of(2010, 5, 5), dto.fechaAlta());
        assertEquals(LocalDate.of(2011, 6, 6), dto.fechaAniversario());
        assertEquals(EstadoMorosidad.MOROSO, dto.estadoMorosidad());
        assertEquals("Calle Nueva", dto.calle());
        assertEquals("55", dto.numero());
        assertEquals(LOCALIDAD, dto.localidad());
        assertEquals("123456789", user.getBps());
        assertEquals("real@empresa.test", user.getEmail());
        assertEquals("real@empresa.test", dto.emailUsuario());
        verify(users, atLeast(2)).save(user);
    }

    @Test
    void updateSocioSinCambiosConservaLosCamposFicticios() {
        User user = user(10L, "cuenta@empresa.test", Role.SOCIO);
        user.setBps("123456789");
        Socio socio = socio(7L, user, "Razón");
        socio.setRut(RUT);
        socio.setTelefono("098 765");
        socio.setEmailContacto("contacto@empresa.test");
        socio.setRubro(comercio);
        socio.setFechaAlta(LocalDate.of(2010, 5, 5));
        socio.setFechaAniversario(LocalDate.of(2011, 6, 6));
        socio.setDireccion(new Direccion("Calle Nueva", "55", LOCALIDAD));
        String todos = "bps,rut,telefono,emailContacto,emailUsuario,calle,numero,localidad,rubro,fechaAlta,fechaAniversario";
        socio.setCamposFicticios(todos);

        SocioResponseDTO dto = service.updateSocio(7L, edicion("123456789", "CUENTA@empresa.test",
                RUT, 1L, LocalDate.of(2010, 5, 5), LocalDate.of(2011, 6, 6)));

        assertEquals(todos, socio.getCamposFicticios());
        assertEquals(11, dto.camposFicticios().size());
        assertEquals("123456789", user.getBps());
        assertEquals("cuenta@empresa.test", user.getEmail());
        verify(users, never()).save(any());
        verify(users, never()).existsByBpsAndIdNot(any(), any());
        verify(users, never()).existsByEmailIgnoreCaseAndIdNot(any(), any());
    }

    @Test
    void updateSocioSinDireccionNiRubroPrevioYConBpsOEmailVaciosNoTocaLaCuenta() {
        User user = user(10L, "cuenta@empresa.test", Role.SOCIO);
        Socio socio = socio(7L, user, "Razón");
        socio.setFechaAlta(null);
        socio.setCamposFicticios("bps,emailUsuario,fechaAlta,rubro");

        SocioResponseDTO dto = service.updateSocio(7L, edicion("  ", "  ", RUT, 1L,
                LocalDate.of(2010, 5, 5), null));

        assertEquals("Calle Nueva", dto.calle());
        verify(direcciones).save(socio.getDireccion());
        assertEquals(LocalDate.of(2010, 5, 5), socio.getFechaAlta());
        assertNull(socio.getFechaAniversario());
        assertEquals("bps,emailUsuario,rubro", socio.getCamposFicticios(),
                "sin rubro previo no se limpia el marcador de rubro");

        service.updateSocio(7L, edicion(null, null, RUT, 1L, null, null));
        assertEquals("bps,emailUsuario,rubro", socio.getCamposFicticios());
        assertEquals(LocalDate.of(2010, 5, 5), socio.getFechaAlta(), "sin fecha de alta en el DTO se conserva la actual");
        verify(users, never()).save(any());
    }

    @Test
    void updateSocioIgnoraCamposDeCuentaSiNoEstanMarcadosOSiNoHayUsuario() {
        User user = user(10L, "cuenta@empresa.test", Role.SOCIO);
        user.setBps("111111111");
        socio(7L, user, "Razón");
        service.updateSocio(7L, edicion("222222222", "otra@empresa.test", RUT, 1L, null, null));
        assertEquals("111111111", user.getBps());
        assertEquals("cuenta@empresa.test", user.getEmail());

        Socio sinUsuario = socio(8L, null, "Sin usuario");
        sinUsuario.setCamposFicticios("bps,emailUsuario");
        SocioResponseDTO dto = service.updateSocio(8L, edicion("222222222", "otra@empresa.test", RUT, 1L, null, null));
        assertEquals(List.of("bps", "emailUsuario"), dto.camposFicticios());
        verify(users, never()).save(any());
    }

    @Test
    void updateSocioValidaBpsYEmailDeCuentaReales() {
        User user = user(10L, "cuenta@incompleto.ccisj.local", Role.SOCIO);
        user.setBps("000000000");
        Socio socio = socio(7L, user, "Razón");
        socio.setCamposFicticios("bps,emailUsuario");

        assertEquals("El número de BPS debe tener al menos 9 dígitos", motivo(() -> service.updateSocio(7L,
                edicion("12345678", null, RUT, 1L, null, null))));

        when(users.existsByBpsAndIdNot("123456789", 10L)).thenReturn(true);
        assertEquals("Ya existe un socio con ese BPS", motivo(() -> service.updateSocio(7L,
                edicion("123456789", null, RUT, 1L, null, null))));
        when(users.existsByBpsAndIdNot("123456789", 10L)).thenReturn(false);
        when(users.existsByCedula("123456789")).thenReturn(true);
        assertEquals(HttpStatus.CONFLICT, status(() -> service.updateSocio(7L,
                edicion("123456789", null, RUT, 1L, null, null))));

        when(users.existsByEmailIgnoreCaseAndIdNot("tomado@empresa.test", 10L)).thenReturn(true);
        assertEquals("Ya existe una cuenta con ese correo", motivo(() -> service.updateSocio(7L,
                edicion(null, " Tomado@Empresa.Test ", RUT, 1L, null, null))));
        assertEquals("000000000", user.getBps());
        assertEquals("cuenta@incompleto.ccisj.local", user.getEmail());
        verify(users, never()).save(any());
    }

    @Test
    void updateSocioPreservaEstadoDeCuenta() {
        User user = user(10L, "cuenta@empresa.test", Role.SOCIO);
        Socio socio = socio(7L, user, "Razón");
        socio.setEstadoMorosidad(EstadoMorosidad.MOROSO);
        SocioResponseDTO dto = service.updateSocio(7L, edicion(null, null, RUT, 1L, null, null));
        assertEquals(EstadoMorosidad.MOROSO, dto.estadoMorosidad());
        assertEquals(EstadoMorosidad.MOROSO, socio.getEstadoMorosidad());
        assertTrue(user.isActivo());
        assertNull(user.getFechaBaja());
        verify(users, never()).save(user);
    }

    @Test
    void updateSocioConCamposOpcionalesVaciosLimpiaMarcadoresYFijaAniversario() {
        User user = user(10L, "cuenta@empresa.test", Role.SOCIO);
        Socio socio = socio(7L, user, "Razón");
        socio.setTelefono("000");
        socio.setEmailContacto("ficticio@empresa.test");
        socio.setDireccion(new Direccion("Calle", "0", LOCALIDAD));
        socio.setFechaAniversario(null);
        socio.setCamposFicticios("telefono,emailContacto,numero,fechaAniversario,calle");

        var dto = new SocioUpdateDTO(null, null, "Razón", RUT, "   ", null, "Calle", null, LOCALIDAD, 1L,
                false, null, LocalDate.of(2015, 8, 25));
        SocioResponseDTO respuesta = service.updateSocio(7L, dto);

        assertNull(respuesta.telefono());
        assertNull(respuesta.emailContacto());
        assertNull(respuesta.numero());
        assertEquals(LocalDate.of(2015, 8, 25), respuesta.fechaAniversario());
        assertEquals("calle", socio.getCamposFicticios(), "la calle no cambió y mantiene su marcador");
    }

    // ---------- habilitar acceso ----------

    @Test
    void habilitarAccesoValidaCuentaYDatosReales() {
        socio(1L, null, "Sin cuenta");
        assertEquals("El socio no tiene cuenta de acceso", motivo(() -> service.habilitarAcceso(1L)));

        Socio conBpsFicticio = socio(2L, user(20L, "real@empresa.test", Role.SOCIO), "BPS ficticio");
        conBpsFicticio.setCamposFicticios("bps");
        assertEquals("Completá el BPS y el email de cuenta reales antes de habilitar el acceso",
                motivo(() -> service.habilitarAcceso(2L)));

        Socio conEmailFicticio = socio(3L, user(21L, "real@empresa.test", Role.SOCIO), "Email ficticio");
        conEmailFicticio.setCamposFicticios("telefono,emailUsuario");
        assertEquals(HttpStatus.CONFLICT, status(() -> service.habilitarAcceso(3L)));

        socio(4L, user(22L, null, Role.SOCIO), "Sin email");
        assertEquals("El socio necesita un email real para enviar las credenciales",
                motivo(() -> service.habilitarAcceso(4L)));

        socio(5L, user(23L, "x@incompleto.ccisj.local", Role.SOCIO), "Email incompleto");
        assertEquals(HttpStatus.CONFLICT, status(() -> service.habilitarAcceso(5L)));
        verify(socios, never()).save(any());
    }

    @Test
    void habilitarAccesoActivaLaCuentaYEnviaCredenciales() {
        User user = user(20L, "real@empresa.test", Role.SOCIO);
        user.setActivo(false);
        user.setCedula("12345678");
        Socio socio = socio(2L, user, "Importada");
        socio.setAccesoHabilitado(false);
        socio.setCamposFicticios("telefono");
        when(mail.isConfigured()).thenReturn(true);

        SocioResponseDTO dto = service.habilitarAcceso(2L);

        assertTrue(socio.isAccesoHabilitado());
        assertTrue(dto.accesoHabilitado());
        assertTrue(user.isActivo());
        assertTrue(user.isDebeCambiarPassword());
        assertEquals("hash:" + dto.passwordProvisional(), user.getPasswordHash());
        assertTrue(dto.credencialesEnviadas());
        assertEquals("Credenciales enviadas automáticamente a real@empresa.test", dto.credencialesEnvioMensaje());
        verify(socios).save(socio);
        verify(users).save(user);
        assertTrue(mailEnviado("real@empresa.test").plain().contains("Usuario (Cédula): 12345678"));
    }

    // ---------- suscripción ----------

    @Test
    void deactivateSocioActivoRegistraBajaYMarcaLaCuenta() {
        User user = user(10L, "cuenta@empresa.test", Role.SOCIO);
        Socio socio = socio(7L, user, "Razón");
        when(history.hasPending(7L)).thenReturn(false);

        SocioResponseDTO dto = service.deactivateSocio(7L);

        verify(history).lock(7L);
        verify(history, never()).cancel(any());
        verify(history).record(7L, "BAJA", LocalDate.of(2026, 5, 10));
        assertEquals(EstadoMorosidad.INACTIVO, socio.getEstadoMorosidad());
        assertFalse(user.isActivo());
        assertNotNull(dto.fechaBaja());
        verify(users).save(user);
        verify(socios).save(socio);
    }

    @Test
    void deactivateSocioYaInactivoNoDuplicaBajaYConservaFechaDeBaja() {
        User user = user(10L, "cuenta@empresa.test", Role.SOCIO);
        user.setActivo(false);
        OffsetDateTime fechaBaja = OffsetDateTime.parse("2025-01-01T10:00:00-03:00");
        user.setFechaBaja(fechaBaja);
        socio(7L, user, "Razón");
        service.deactivateSocio(7L);
        assertEquals(fechaBaja, user.getFechaBaja());
        verify(history, never()).record(any(), any(), any());

        Socio sinUsuario = socio(8L, null, "Sin usuario");
        sinUsuario.setEstadoMorosidad(EstadoMorosidad.MOROSO);
        SocioResponseDTO dto = service.deactivateSocio(8L);
        assertEquals(EstadoMorosidad.INACTIVO, dto.estadoMorosidad());
        verify(history, never()).record(any(), any(), any());
        verify(users, times(1)).save(any());
    }

    @Test
    void activateSocioRechazaSiYaEstaActivo() {
        socio(7L, user(10L, "cuenta@empresa.test", Role.SOCIO), "Razón");
        assertEquals("El socio ya tiene una suscripción activa",
                motivo(() -> service.activateSocio(7L, LocalDate.of(2026, 5, 1))));
        verify(history, never()).validateReentry(any(), any());
    }

    @Test
    void activateSocioConFechaFuturaProgramaElReingreso() {
        User user = user(10L, "cuenta@empresa.test", Role.SOCIO);
        user.setActivo(false);
        socio(7L, user, "Razón");
        LocalDate futura = LocalDate.of(2026, 6, 1);

        SocioResponseDTO dto = service.activateSocio(7L, futura);

        verify(history).validateReentry(7L, futura);
        verify(history).schedule(7L, futura);
        verify(history, never()).record(any(), any(), any());
        assertFalse(dto.activo());
        assertFalse(user.isActivo());
        verifyNoInteractions(caja);
    }

    @Test
    void activateSocioConFechaPasadaReactivaYRecalculaMorosidad() {
        User user = user(10L, "cuenta@empresa.test", Role.SOCIO);
        user.setActivo(false);
        user.setFechaBaja(OffsetDateTime.now());
        Socio socio = socio(7L, user, "Razón");
        socio.setEstadoMorosidad(EstadoMorosidad.INACTIVO);
        LocalDate hoy = LocalDate.of(2026, 5, 10);

        SocioResponseDTO dto = service.activateSocio(7L, hoy);

        assertTrue(dto.activo());
        assertTrue(user.isActivo());
        assertNull(user.getFechaBaja());
        assertEquals(EstadoMorosidad.AL_DIA, socio.getEstadoMorosidad());
        InOrder orden = inOrder(history, socios, caja);
        orden.verify(history).record(7L, "REINGRESO", hoy);
        orden.verify(socios).save(socio);
        orden.verify(caja).actualizarMorosidad(socio);
    }

    @Test
    void activateSocioSinCuentaSoloCambiaElEstado() {
        Socio moroso = socio(7L, null, "Sin cuenta");
        moroso.setEstadoMorosidad(EstadoMorosidad.INACTIVO);
        service.activateSocio(7L, LocalDate.of(2026, 5, 1));
        assertEquals(EstadoMorosidad.AL_DIA, moroso.getEstadoMorosidad());
        verify(users, never()).save(any());

        User inactivo = user(10L, "cuenta@empresa.test", Role.SOCIO);
        inactivo.setActivo(false);
        Socio conDeuda = socio(8L, inactivo, "Con deuda");
        conDeuda.setEstadoMorosidad(EstadoMorosidad.MOROSO);
        service.activateSocio(8L, LocalDate.of(2026, 5, 1));
        assertEquals(EstadoMorosidad.MOROSO, conDeuda.getEstadoMorosidad(),
                "markActive solo saca del estado INACTIVO");
        assertTrue(inactivo.isActivo());
    }

    @Test
    void applyScheduledReentryAplicaSoloSiHayFechaVencida() {
        User user = user(10L, "cuenta@empresa.test", Role.SOCIO);
        user.setActivo(false);
        Socio socio = socio(7L, user, "Razón");
        socio.setEstadoMorosidad(EstadoMorosidad.INACTIVO);

        when(history.applyDue(7L)).thenReturn(null);
        service.applyScheduledReentry(7L);
        assertFalse(user.isActivo());
        verify(history, never()).record(any(), any(), any());

        LocalDate fecha = LocalDate.of(2026, 5, 9);
        when(history.applyDue(7L)).thenReturn(fecha);
        service.applyScheduledReentry(7L);
        assertTrue(user.isActivo());
        assertEquals(EstadoMorosidad.AL_DIA, socio.getEstadoMorosidad());
        verify(history).record(7L, "REINGRESO", fecha);
        verify(socios).save(socio);
        verify(caja).actualizarMorosidad(socio);

        assertEquals("El socio ya está activo; revisar reingreso programado",
                motivo(() -> service.applyScheduledReentry(7L)));
        verify(history, times(3)).lock(7L);
    }

    @Test
    void cancelScheduledReentryBloqueaYCancela() {
        service.cancelScheduledReentry(7L);
        InOrder orden = inOrder(history);
        orden.verify(history).lock(7L);
        orden.verify(history).cancel(7L);
    }

    // ---------- claves y credenciales ----------

    @Test
    void resetClaveProvisionalGeneraClaveTemporal() {
        socio(1L, null, "Sin cuenta");
        assertEquals("El socio no tiene cuenta de acceso", motivo(() -> service.resetClaveProvisional(1L)));

        User inactivo = user(20L, "i@empresa.test", Role.SOCIO);
        inactivo.setActivo(false);
        socio(2L, inactivo, "Inactivo");
        assertEquals("Reactiva al socio antes de restablecer la clave", motivo(() -> service.resetClaveProvisional(2L)));

        User user = user(21L, "a@empresa.test", Role.SOCIO);
        socio(3L, user, "Activo");
        SocioResponseDTO dto = service.resetClaveProvisional(3L);
        assertNotNull(dto.passwordProvisional());
        assertNull(dto.credencialesEnviadas());
        assertEquals("hash:" + dto.passwordProvisional(), user.getPasswordHash());
        assertTrue(user.isDebeCambiarPassword());
        assertNotNull(user.getPasswordProvisionalHasta());
        verify(users).save(user);
        verifyNoInteractions(mail);
    }

    @Test
    void enviarCredencialesValidaCuentaYDestinatario() {
        socio(1L, null, "Sin cuenta");
        assertEquals("El socio no tiene cuenta de acceso", motivo(() -> service.enviarCredenciales(1L)));

        User inactivo = user(20L, "i@empresa.test", Role.SOCIO);
        inactivo.setActivo(false);
        socio(2L, inactivo, "Inactivo");
        assertEquals("Reactiva al socio antes de enviar las credenciales", motivo(() -> service.enviarCredenciales(2L)));

        Socio sinEmail = socio(3L, user(21L, "  ", Role.SOCIO), "Sin email");
        sinEmail.setEmailContacto(" ");
        assertEquals(HttpStatus.BAD_REQUEST, status(() -> service.enviarCredenciales(3L)));
        verify(users, never()).save(any());
        verifyNoInteractions(mail);
    }

    @Test
    void enviarCredencialesAlContactoConUsuarioBps() {
        User user = user(20L, "cuenta@empresa.test", Role.SOCIO);
        user.setBps("123456789");
        Socio socio = socio(2L, user, null);
        socio.setEmailContacto(" contacto@empresa.test ");

        var dto = service.enviarCredenciales(2L);

        assertEquals("contacto@empresa.test", dto.destinatario());
        assertEquals("Credenciales enviadas a contacto@empresa.test", dto.mensaje());
        assertEquals("hash:" + dto.passwordProvisional(), user.getPasswordHash());
        InOrder orden = inOrder(users, mail);
        orden.verify(users).save(user);
        orden.verify(mail).requireConfigured();
        MailTemplate.Content content = mailEnviado("contacto@empresa.test");
        assertTrue(content.plain().contains("Hola, socio/a:"));
        assertTrue(content.plain().contains("Usuario (BPS): 123456789"));
    }

    @Test
    void enviarCredencialesUsaElEmailDeCuentaSiNoHayContactoYPropagaFallaDeConfiguracion() {
        User user = user(20L, " cuenta@empresa.test ", Role.SOCIO);
        user.setCedula("  ");
        user.setBps("123456789");
        socio(2L, user, "  Empresa  ");

        var dto = service.enviarCredenciales(2L);
        assertEquals("cuenta@empresa.test", dto.destinatario());
        MailTemplate.Content content = mailEnviado("cuenta@empresa.test");
        assertTrue(content.plain().contains("Hola, Empresa:"));
        assertTrue(content.plain().contains("Usuario (BPS): 123456789"));

        doThrow(new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Correo no configurado"))
                .when(mail).requireConfigured();
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, status(() -> service.enviarCredenciales(2L)));
    }
}

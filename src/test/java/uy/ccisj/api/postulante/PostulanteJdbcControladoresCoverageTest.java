package uy.ccisj.api.postulante;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;
import uy.ccisj.api.auth.ProfilePhotoService;
import uy.ccisj.api.storage.S3StorageService;
import uy.ccisj.api.support.DatabaseTestConfig;
import uy.ccisj.api.user.Role;
import uy.ccisj.api.user.User;
import uy.ccisj.api.user.UserRepository;

/**
 * Controladores del módulo postulante que consultan SQL nativo (perfiles, datos personales y vista de administración)
 * contra un schema propio en el Postgres de prueba.
 */
@EnabledIfSystemProperty(named = "testJdbcUrl", matches = ".+")
class PostulanteJdbcControladoresCoverageTest {
    private static final String ANA = "cov-ana@example.test";
    private static final String BRUNO = "cov-bruno@example.test";
    private static final String CARLA = "cov-carla@example.test";
    private static final String ADMIN = "cov-admin@example.test";
    private static final byte[] FOTO = new byte[] {1, 2, 3};

    private static JdbcTemplate jdbc;
    private static Long anaId;
    private static Long brunoId;
    private static Long carlaId;
    private static Long perfilAna;
    private static Long perfilBruno;
    private static Long perfilCarla;
    private static Long cvAna;
    private static Long cvBruno;
    private static Long cvCarla;

    private final UserRepository users = mock(UserRepository.class);
    private final S3StorageService storage = mock(S3StorageService.class);
    private final PostulanteRepository postulantes = mock(PostulanteRepository.class);
    private final ProfilePhotoService photos = mock(ProfilePhotoService.class);
    private AdminPostulanteController admin;
    private PostulantePerfilController perfiles;
    private PostulanteDatosPersonalesController datos;

    @BeforeAll
    static void migrar() {
        String schema = "cov_postulante_" + UUID.randomUUID().toString().replace("-", "");
        DriverManagerDataSource dataSource = DatabaseTestConfig.dataSource(schema);
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("CREATE EXTENSION IF NOT EXISTS pgcrypto WITH SCHEMA public");
        Flyway.configure().dataSource(dataSource).schemas(schema).load().migrate();

        anaId = postulante(ANA, "Ana Pérez", "cov-1", true, true);
        jdbc.update("UPDATE postulantes SET fecha_nacimiento = current_date - interval '30 years' WHERE id = ?", anaId);
        jdbc.update("UPDATE usuarios SET foto_perfil = ?, foto_mime = 'image/png' WHERE email = ?", FOTO, ANA);
        brunoId = postulante(BRUNO, "Bruno Díaz", "cov-2", false, true);
        jdbc.update("UPDATE usuarios SET google_photo_url = 'https://google/foto' WHERE email = ?", BRUNO);
        carlaId = postulante(CARLA, "Carla Ruiz", "cov-3", true, false);

        perfilAna = perfil(anaId, "Vendedora");
        jdbc.update("""
                INSERT INTO perfil_rubros (perfil_id, rubro_id)
                SELECT ?, id FROM rubros_empleo WHERE nombre_rubro IN ('Ventas', 'Logistica')
                """, perfilAna);
        perfilBruno = perfil(brunoId, "Chofer");
        perfilCarla = perfil(carlaId, "Recepción");
        cvAna = cv(anaId, perfilAna, "postulantes/ana/cv.pdf", 2048L);
        cvBruno = cv(brunoId, perfilBruno, null, null);
        cvCarla = cv(carlaId, perfilCarla, "   ", 3L * 1024 * 1024);
    }

    @BeforeEach
    void setUp() {
        when(users.findByEmailIgnoreCase(ADMIN)).thenReturn(Optional.of(new User(ADMIN, null, "x", Role.ADMIN)));
        User adminInactivo = new User("cov-baja@example.test", null, "x", Role.ADMIN);
        adminInactivo.setActivo(false);
        when(users.findByEmailIgnoreCase("cov-baja@example.test")).thenReturn(Optional.of(adminInactivo));
        when(users.findByEmailIgnoreCase(ANA)).thenReturn(Optional.of(new User(ANA, null, "x", Role.POSTULANTE)));
        when(storage.presignedDownloadUrl(anyString(), eq(Duration.ofMinutes(20))))
                .thenAnswer(invocation -> "https://firmada/" + invocation.getArgument(0));
        admin = new AdminPostulanteController(jdbc, users, storage, 20);

        Postulante ana = new Postulante(null, "Ana Pérez", "cov-1", "099", "San José");
        ReflectionTestUtils.setField(ana, "id", anaId);
        Postulante bruno = new Postulante(null, "Bruno Díaz", "cov-2", "099", "San José");
        ReflectionTestUtils.setField(bruno, "id", brunoId);
        when(postulantes.findByUsuarioEmailIgnoreCase(ANA)).thenReturn(Optional.of(ana));
        when(postulantes.findByUsuarioEmailIgnoreCase(BRUNO)).thenReturn(Optional.of(bruno));
        perfiles = new PostulantePerfilController(jdbc, postulantes);
        datos = new PostulanteDatosPersonalesController(jdbc, photos);
    }

    @Test
    void adminListaPerfilesConEstadoEnBolsaRubrosEdadYCv() {
        List<AdminPostulanteController.ApplicantProfile> lista = admin.list(auth(ADMIN));

        AdminPostulanteController.ApplicantProfile ana = porPerfil(lista, perfilAna);
        assertThat(ana.applicantId()).isEqualTo(anaId);
        assertThat(ana.fullName()).isEqualTo("Ana Pérez");
        assertThat(ana.initials()).isEqualTo("AP");
        assertThat(ana.email()).isEqualTo(ANA);
        assertThat(ana.sectors()).containsExactlyInAnyOrder("Ventas", "Logistica");
        assertThat(ana.categoryLabel()).isEqualTo(ana.sectors().getFirst().toUpperCase());
        assertThat(ana.age()).isEqualTo(30);
        assertThat(ana.hasCv()).isTrue();
        assertThat(ana.cvFileName()).isEqualTo("cv-" + cvAna + ".pdf");
        assertThat(ana.cvMeta()).isEqualTo("PDF oficial · 2 KB");
        assertThat(ana.fotoUrl()).isEqualTo("data:image/png;base64,AQID");
        assertThat(ana.cuentaActiva()).isTrue();
        assertThat(ana.visibleEnBolsa()).isTrue();
        assertThat(ana.estado()).isEqualTo("ACTIVO");

        AdminPostulanteController.ApplicantProfile bruno = porPerfil(lista, perfilBruno);
        assertThat(bruno.sectors()).isEmpty();
        assertThat(bruno.categoryLabel()).isEqualTo("OTROS");
        assertThat(bruno.age()).isNull();
        assertThat(bruno.cvMeta()).isEqualTo("PDF oficial");
        assertThat(bruno.fotoUrl()).isEqualTo("https://google/foto");
        assertThat(bruno.cuentaActiva()).isFalse();
        assertThat(bruno.estado()).isEqualTo("INACTIVO");

        AdminPostulanteController.ApplicantProfile carla = porPerfil(lista, perfilCarla);
        assertThat(carla.cuentaActiva()).isTrue();
        assertThat(carla.visibleEnBolsa()).isFalse();
        assertThat(carla.estado()).isEqualTo("INACTIVO");
        assertThat(carla.cvMeta()).isEqualTo("PDF oficial · 3.0 MB");
    }

    @Test
    void adminListaCvsDelPerfilYFirmaLaDescarga() {
        List<AdminPostulanteController.ProfileCv> cvs = admin.listCvs(auth(ADMIN), perfilAna);
        assertThat(cvs).singleElement().satisfies(cv -> {
            assertThat(cv.id()).isEqualTo(cvAna);
            assertThat(cv.fileName()).isEqualTo("cv-" + cvAna + ".pdf");
            assertThat(cv.meta()).isEqualTo("PDF oficial · 2 KB");
            assertThat(cv.active()).isTrue();
            assertThat(cv.uploadedAt()).isNotNull();
        });
        assertThat(admin.listCvs(auth(ADMIN), perfilBruno)).singleElement()
                .satisfies(cv -> assertThat(cv.meta()).isEqualTo("PDF oficial"));

        assertThat(admin.downloadCv(auth(ADMIN), perfilAna, cvAna).url())
                .isEqualTo("https://firmada/postulantes/ana/cv.pdf");
    }

    @Test
    void adminDescargaDevuelveNotFoundSiElCvNoExisteNoEsDelPerfilOFaltaLaRuta() {
        assertStatus(HttpStatus.NOT_FOUND, () -> admin.downloadCv(auth(ADMIN), perfilBruno, cvAna));
        assertStatus(HttpStatus.NOT_FOUND, () -> admin.downloadCv(auth(ADMIN), perfilBruno, cvBruno));
        assertStatus(HttpStatus.NOT_FOUND, () -> admin.downloadCv(auth(ADMIN), perfilCarla, cvCarla));
        verify(storage, never()).presignedDownloadUrl(anyString(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void adminExigeCuentaDeAdministradorActiva() {
        for (String email : List.of("cov-desconocido@example.test", "cov-baja@example.test", ANA)) {
            assertStatus(HttpStatus.FORBIDDEN, () -> admin.list(auth(email)));
            assertStatus(HttpStatus.FORBIDDEN, () -> admin.listCvs(auth(email), perfilAna));
            assertStatus(HttpStatus.FORBIDDEN, () -> admin.downloadCv(auth(email), perfilAna, cvAna));
        }
    }

    @Test
    void perfilSeCreaConRubrosValidosYCamposOpcionalesNormalizados() {
        var creado = perfiles.create(auth(BRUNO), new PostulantePerfilController.PerfilRequest(
                "  Repartidor ", "PART_TIME", true, "  ", " Cadete ", null, false,
                List.of("Repartidores", "Rubro inexistente")));

        assertThat(creado.id()).isNotNull();
        assertThat(creado.nombre()).isEqualTo("Repartidor");
        assertThat(creado.disponibilidadHoraria()).isEqualTo("PART_TIME");
        assertThat(creado.tieneVehiculo()).isTrue();
        assertThat(creado.libreta()).isNull();
        assertThat(creado.ultimoEmpleo()).isEqualTo("Cadete");
        assertThat(creado.descripcionExperiencia()).isNull();
        assertThat(creado.visible()).isFalse();
        // El alta ignora rubros desconocidos en lugar de fallar.
        assertThat(creado.rubros()).containsExactly("Repartidores");

        var sinRubros = perfiles.create(auth(BRUNO), new PostulantePerfilController.PerfilRequest(
                "Mozo", "INDEFINIDO", false, "B", "Mozo", "Eventos", true, null));
        assertThat(sinRubros.rubros()).isEmpty();
        assertThat(sinRubros.descripcionExperiencia()).isEqualTo("Eventos");

        assertThat(perfiles.list(auth(BRUNO)))
                .extracting(PostulantePerfilController.PerfilResponse::id)
                .contains(perfilBruno, creado.id(), sinRubros.id());
    }

    @Test
    void perfilListaSoloLosDelPostulanteConSusRubros() {
        List<PostulantePerfilController.PerfilResponse> lista = perfiles.list(auth(ANA));

        assertThat(lista).extracting(PostulantePerfilController.PerfilResponse::id)
                .startsWith(perfilAna)
                .doesNotContain(perfilBruno, perfilCarla);
        assertThat(lista.getFirst().nombre()).isEqualTo("Vendedora");
        assertThat(lista.getFirst().disponibilidadHoraria()).isEqualTo("FULL_TIME");
        assertThat(lista.getFirst().rubros()).containsExactly("Logistica", "Ventas");
    }

    @Test
    void perfilValidaNombreCantidadDeRubrosYCuentaDePostulante() {
        var sinNombre = new PostulantePerfilController.PerfilRequest(null, "FULL_TIME", false, null, null, null, true, null);
        var nombreVacio = new PostulantePerfilController.PerfilRequest("  ", "FULL_TIME", false, null, null, null, true, null);
        var cincoRubros = new PostulantePerfilController.PerfilRequest("X", "FULL_TIME", false, null, null, null, true,
                List.of("Ventas", "Repartidores", "Administracion", "Choferes", "Contabilidad"));

        for (var invalido : List.of(sinNombre, nombreVacio, cincoRubros)) {
            assertStatus(HttpStatus.BAD_REQUEST, () -> perfiles.create(auth(ANA), invalido));
            assertStatus(HttpStatus.BAD_REQUEST, () -> perfiles.update(auth(ANA), perfilAna, invalido));
        }
        assertStatus(HttpStatus.FORBIDDEN, () -> perfiles.list(auth("cov-socio@example.test")));
        assertThat(perfiles.list(auth(ANA)).getFirst().nombre()).isEqualTo("Vendedora");
    }

    @Test
    void perfilSeActualizaReemplazandoRubros() {
        Long perfil = perfil(anaId, "Temporal");
        var actualizado = perfiles.update(auth(ANA), perfil, new PostulantePerfilController.PerfilRequest(
                " Administrativa ", "PART_TIME", true, " A ", "  ", " Facturación ", false,
                List.of("Administracion", "Contabilidad")));

        assertThat(actualizado.nombre()).isEqualTo("Administrativa");
        assertThat(actualizado.libreta()).isEqualTo("A");
        assertThat(actualizado.ultimoEmpleo()).isNull();
        assertThat(actualizado.descripcionExperiencia()).isEqualTo("Facturación");
        assertThat(actualizado.visible()).isFalse();
        assertThat(actualizado.rubros()).containsExactly("Administracion", "Contabilidad");

        var sinRubros = perfiles.update(auth(ANA), perfil, new PostulantePerfilController.PerfilRequest(
                "Administrativa", "FULL_TIME", false, null, null, null, true, null));
        assertThat(sinRubros.rubros()).isEmpty();
        assertThat(sinRubros.visible()).isTrue();
    }

    @Test
    void perfilNoActualizaPerfilesAjenosNiRubrosInvalidos() {
        var request = new PostulantePerfilController.PerfilRequest(
                "Intruso", "FULL_TIME", false, null, null, null, true, List.of());
        assertStatus(HttpStatus.NOT_FOUND, () -> perfiles.update(auth(ANA), perfilBruno, request));
        assertThat(jdbc.queryForObject("SELECT nombre FROM perfiles_laborales WHERE id = ?", String.class, perfilBruno))
                .isEqualTo("Chofer");

        Long perfil = perfil(anaId, "Con rubro inválido");
        ResponseStatusException error = assertStatus(HttpStatus.BAD_REQUEST, () -> perfiles.update(auth(ANA), perfil,
                new PostulantePerfilController.PerfilRequest("Con rubro inválido", "FULL_TIME", false, null, null, null,
                        true, List.of("No existe"))));
        assertThat(error.getReason()).isEqualTo("Rubro no valido: No existe");
    }

    @Test
    void datosPersonalesSeConsultanYActualizan() {
        var actuales = datos.get(auth(CARLA));
        assertThat(actuales.nombreCompleto()).isEqualTo("Carla Ruiz");
        assertThat(actuales.email()).isEqualTo(CARLA);
        assertThat(actuales.fotoUrl()).isNull();
        assertThat(datos.get(auth("cov-nadie@example.test"))).isNull();

        var actualizados = datos.update(auth(CARLA), new PostulanteDatosPersonalesController.DatosPersonalesRequest(
                " Carla Ruiz Gómez ", " cov-3b ", " 098765432 ", " Libertad "));
        assertThat(actualizados.nombreCompleto()).isEqualTo("Carla Ruiz Gómez");
        assertThat(actualizados.cedulaIdentidad()).isEqualTo("cov-3b");
        assertThat(actualizados.telefono()).isEqualTo("098765432");
        assertThat(actualizados.zonaResidencia()).isEqualTo("Libertad");
        assertThat(datos.get(auth(ANA)).fotoUrl()).isEqualTo("data:image/png;base64,AQID");
    }

    @Test
    void datosPersonalesExigenNombreYPostulanteExistente() {
        for (String nombre : new String[] {null, "   "}) {
            assertStatus(HttpStatus.BAD_REQUEST, () -> datos.update(auth(CARLA),
                    new PostulanteDatosPersonalesController.DatosPersonalesRequest(nombre, "c", "t", "z")));
        }
        assertStatus(HttpStatus.NOT_FOUND, () -> datos.update(auth("cov-nadie@example.test"),
                new PostulanteDatosPersonalesController.DatosPersonalesRequest("Nadie", "x", "y", "z")));
    }

    @Test
    void datosPersonalesRechazanCamposObligatoriosVaciosConBadRequestYNoConError500() {
        // cedula_identidad, telefono y zona_residencia son NOT NULL en la tabla postulantes.
        assertThat(assertStatus(HttpStatus.BAD_REQUEST, () -> datos.update(auth(ANA),
                new PostulanteDatosPersonalesController.DatosPersonalesRequest("Ana", "  ", "099", "San José"))).getReason())
                .isEqualTo("La cédula de identidad es obligatoria");
        assertThat(assertStatus(HttpStatus.BAD_REQUEST, () -> datos.update(auth(ANA),
                new PostulanteDatosPersonalesController.DatosPersonalesRequest("Ana", "cov-1", null, "San José"))).getReason())
                .isEqualTo("El teléfono es obligatorio");
        assertThat(assertStatus(HttpStatus.BAD_REQUEST, () -> datos.update(auth(ANA),
                new PostulanteDatosPersonalesController.DatosPersonalesRequest("Ana", "cov-1", "099", ""))).getReason())
                .isEqualTo("La zona de residencia es obligatoria");
        assertThat(datos.get(auth(ANA)).cedulaIdentidad()).isEqualTo("cov-1");
    }

    @Test
    void datosPersonalesConCedulaDeOtroPostulanteDevuelvenConflicto() {
        assertThat(assertStatus(HttpStatus.CONFLICT, () -> datos.update(auth(ANA),
                new PostulanteDatosPersonalesController.DatosPersonalesRequest("Ana Pérez", "cov-2", "099", "San José"))).getReason())
                .isEqualTo("La cédula ya está registrada por otro postulante");
        assertThat(datos.get(auth(ANA)).cedulaIdentidad()).isEqualTo("cov-1");
    }

    @Test
    void fotoSoloSeSubeParaPostulantesExistentes() {
        MockMultipartFile foto = new MockMultipartFile("file", "foto.png", "image/png", FOTO);

        var respuesta = datos.uploadPhoto(auth(BRUNO), foto);
        assertThat(respuesta.email()).isEqualTo(BRUNO);
        verify(photos).upload(BRUNO, foto);

        assertStatus(HttpStatus.NOT_FOUND, () -> datos.uploadPhoto(auth("cov-nadie@example.test"), foto));
        verify(photos, never()).upload(eq("cov-nadie@example.test"), org.mockito.ArgumentMatchers.any());
    }

    private static Long postulante(String email, String nombre, String cedula, boolean activo, boolean visible) {
        Long usuario = jdbc.queryForObject("""
                INSERT INTO usuarios (email, password_hash, rol, activo) VALUES (?, 'test-only', 'POSTULANTE', ?) RETURNING id
                """, Long.class, email, activo);
        return jdbc.queryForObject("""
                INSERT INTO postulantes (usuario_id, nombre_completo, cedula_identidad, telefono, zona_residencia, visible)
                VALUES (?, ?, ?, '091111111', 'San José', ?) RETURNING id
                """, Long.class, usuario, nombre, cedula, visible);
    }

    private static Long perfil(Long postulanteId, String nombre) {
        return jdbc.queryForObject("""
                INSERT INTO perfiles_laborales (postulante_id, nombre, disponibilidad_horaria) VALUES (?, ?, 'FULL_TIME')
                RETURNING id
                """, Long.class, postulanteId, nombre);
    }

    private static Long cv(Long postulanteId, Long perfilId, String ruta, Long size) {
        Long id = jdbc.queryForObject("""
                INSERT INTO cvs (postulante_id, perfil_laboral_id, ruta_archivo_cv, size_bytes, activo, version)
                VALUES (?, ?, ?, ?, TRUE, 1) RETURNING id
                """, Long.class, postulanteId, perfilId, ruta, size);
        jdbc.update("UPDATE cvs SET nombre_archivo = ? WHERE id = ?", "cv-" + id + ".pdf", id);
        return id;
    }

    private static AdminPostulanteController.ApplicantProfile porPerfil(
            List<AdminPostulanteController.ApplicantProfile> lista, Long perfilId) {
        return lista.stream().filter(p -> p.id().equals(perfilId)).findFirst().orElseThrow();
    }

    private static UsernamePasswordAuthenticationToken auth(String email) {
        return new UsernamePasswordAuthenticationToken(email, null);
    }

    private static ResponseStatusException assertStatus(HttpStatus expected, Runnable action) {
        ResponseStatusException[] capturada = new ResponseStatusException[1];
        assertThatThrownBy(action::run).isInstanceOfSatisfying(ResponseStatusException.class, e -> {
            assertThat(e.getStatusCode()).isEqualTo(expected);
            capturada[0] = e;
        });
        return capturada[0];
    }
}

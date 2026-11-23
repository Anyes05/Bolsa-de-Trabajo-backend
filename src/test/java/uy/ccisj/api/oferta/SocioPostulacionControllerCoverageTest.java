package uy.ccisj.api.oferta;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.ResultSet;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.server.ResponseStatusException;
import uy.ccisj.api.postulante.Cv;
import uy.ccisj.api.socio.Socio;
import uy.ccisj.api.socio.SocioRepository;
import uy.ccisj.api.storage.S3StorageService;
import uy.ccisj.api.user.Role;
import uy.ccisj.api.user.User;
import uy.ccisj.api.user.UserRepository;

class SocioPostulacionControllerCoverageTest {
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final PostulacionRepository postulaciones = mock(PostulacionRepository.class);
    private final UserRepository usuarios = mock(UserRepository.class);
    private final SocioRepository socios = mock(SocioRepository.class);
    private final S3StorageService storage = mock(S3StorageService.class);
    private SocioPostulacionController controller;

    @BeforeEach
    void setUp() {
        controller = new SocioPostulacionController(jdbc, postulaciones, usuarios, socios, storage, 10);
        User usuario = usuario(Role.SOCIO, true);
        when(usuarios.findByEmailIgnoreCase("socio@example.test")).thenReturn(Optional.of(usuario));
        Socio socio = mock(Socio.class);
        when(socio.getId()).thenReturn(2L);
        when(socios.findByUsuarioId(1L)).thenReturn(Optional.of(socio));
    }

    @Test
    @SuppressWarnings("unchecked")
    void listaPostulacionesRecibidasMapeandoCadaColumna() throws Exception {
        ResultSet conCv = filaBase("Ana María Pérez", "Ventas|Logística");
        when(conCv.getObject("cv_id", Long.class)).thenReturn(30L);
        when(conCv.getString("nombre_archivo")).thenReturn("cv-ana.pdf");
        when(conCv.getLong("size_bytes")).thenReturn(2048L);
        when(conCv.wasNull()).thenReturn(false);
        ResultSet sinCv = filaBase(null, "");
        when(sinCv.getObject("cv_id", Long.class)).thenReturn(null);
        when(sinCv.getLong("size_bytes")).thenReturn(0L);
        when(sinCv.wasNull()).thenReturn(true);
        when(jdbc.query(anyString(), any(RowMapper.class), eq(2L), eq(7L))).thenAnswer(invocation -> {
            RowMapper<SocioPostulacionController.ReceivedApplication> mapper = invocation.getArgument(1);
            List<SocioPostulacionController.ReceivedApplication> filas = new ArrayList<>();
            filas.add(mapper.mapRow(conCv, 0));
            filas.add(mapper.mapRow(sinCv, 1));
            return filas;
        });

        var resultado = controller.list(auth(), 7L);

        assertThat(resultado).hasSize(2);
        var primera = resultado.get(0);
        assertThat(primera.id()).isEqualTo(5L);
        assertThat(primera.fullName()).isEqualTo("Ana María Pérez");
        assertThat(primera.initials()).isEqualTo("AM");
        assertThat(primera.profileId()).isEqualTo(9L);
        assertThat(primera.profileName()).isEqualTo("Perfil comercial");
        assertThat(primera.location()).isEqualTo("Libertad");
        assertThat(primera.tags()).containsExactly("Ventas", "Logística");
        assertThat(primera.status()).isEqualTo("RECIBIDA");
        assertThat(primera.appliedOn()).isEqualTo("3/2/2026");
        assertThat(primera.availability()).isEqualTo("FULL_TIME");
        assertThat(primera.hasVehicle()).isTrue();
        assertThat(primera.license()).isEqualTo("A");
        assertThat(primera.latestJob()).isEqualTo("Cajera");
        assertThat(primera.experienceSummary()).isEqualTo("Cinco años");
        assertThat(primera.identityCard()).isEqualTo("1.234.567-8");
        assertThat(primera.phone()).isEqualTo("099123456");
        assertThat(primera.cvId()).isEqualTo(30L);
        assertThat(primera.cvFileName()).isEqualTo("cv-ana.pdf");
        assertThat(primera.cvMeta()).isEqualTo("PDF oficial · 2 KB");

        var segunda = resultado.get(1);
        assertThat(segunda.initials()).isEmpty();
        assertThat(segunda.tags()).isEmpty();
        assertThat(segunda.cvId()).isNull();
        assertThat(segunda.cvMeta()).isEqualTo("PDF oficial");
    }

    @Test
    void aplicaTodasLasTransicionesDeEstadoPermitidasYRechazaLasDemas() {
        for (EstadoPostulacion actual : EstadoPostulacion.values()) {
            for (EstadoPostulacion siguiente : EstadoPostulacion.values()) {
                Postulacion postulacion = new Postulacion(null, null);
                postulacion.setEstado(actual);
                when(postulaciones.findByIdAndOfertaSocioId(5L, 2L)).thenReturn(Optional.of(postulacion));
                if (esperada(actual, siguiente)) {
                    var respuesta = controller.updateStatus(auth(), 5L, siguiente);
                    assertThat(respuesta.status()).as("%s -> %s", actual, siguiente).isEqualTo(siguiente);
                    assertThat(postulacion.getEstado()).isEqualTo(siguiente);
                } else {
                    assertStatus(HttpStatus.CONFLICT, () -> controller.updateStatus(auth(), 5L, siguiente));
                    assertThat(postulacion.getEstado()).as("%s -> %s", actual, siguiente).isEqualTo(actual);
                }
            }
        }
    }

    @Test
    void descargaDeCvInexistenteOSinRutaDevuelveNotFound() {
        when(postulaciones.findByIdAndOfertaSocioId(99L, 2L)).thenReturn(Optional.empty());
        assertStatus(HttpStatus.NOT_FOUND, () -> controller.downloadCv(auth(), 99L));

        Postulacion postulacion = new Postulacion(null, null);
        Cv cv = mock(Cv.class);
        when(cv.getRutaArchivoCv()).thenReturn(null);
        postulacion.setCv(cv);
        when(postulaciones.findByIdAndOfertaSocioId(5L, 2L)).thenReturn(Optional.of(postulacion));
        assertThatThrownBy(() -> controller.downloadCv(auth(), 5L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("no tiene un archivo disponible");
        verify(storage, never()).presignedDownloadUrl(anyString(), any());
    }

    @Test
    void rechazaUsuarioInactivoOConRolDistintoDeSocio() {
        User inactivo = usuario(Role.SOCIO, false);
        when(usuarios.findByEmailIgnoreCase("inactivo@example.test")).thenReturn(Optional.of(inactivo));
        User admin = usuario(Role.ADMIN, true);
        when(usuarios.findByEmailIgnoreCase("admin@example.test")).thenReturn(Optional.of(admin));

        assertStatus(HttpStatus.FORBIDDEN, () -> controller.list(
                new UsernamePasswordAuthenticationToken("inactivo@example.test", null), 7L));
        assertStatus(HttpStatus.FORBIDDEN, () -> controller.updateStatus(
                new UsernamePasswordAuthenticationToken("admin@example.test", null), 5L, EstadoPostulacion.REVISADA));
    }

    private static boolean esperada(EstadoPostulacion actual, EstadoPostulacion siguiente) {
        if (actual == siguiente) return true;
        return switch (actual) {
            case RECIBIDA -> siguiente == EstadoPostulacion.REVISADA || siguiente == EstadoPostulacion.DESCARTADA;
            case REVISADA -> siguiente == EstadoPostulacion.CONTACTADA || siguiente == EstadoPostulacion.SELECCIONADA
                    || siguiente == EstadoPostulacion.DESCARTADA;
            case CONTACTADA -> siguiente == EstadoPostulacion.SELECCIONADA || siguiente == EstadoPostulacion.DESCARTADA;
            case SELECCIONADA, DESCARTADA -> false;
        };
    }

    private ResultSet filaBase(String nombre, String rubros) throws Exception {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getLong("id")).thenReturn(5L);
        when(rs.getString("nombre_completo")).thenReturn(nombre);
        when(rs.getLong("perfil_id")).thenReturn(9L);
        when(rs.getString("perfil_nombre")).thenReturn("Perfil comercial");
        when(rs.getString("zona_residencia")).thenReturn("Libertad");
        when(rs.getString("rubros")).thenReturn(rubros);
        when(rs.getString("estado")).thenReturn("RECIBIDA");
        when(rs.getObject("fecha", OffsetDateTime.class))
                .thenReturn(OffsetDateTime.of(2026, 2, 3, 10, 0, 0, 0, ZoneOffset.ofHours(-3)));
        when(rs.getString("disponibilidad_horaria")).thenReturn("FULL_TIME");
        when(rs.getBoolean("tiene_vehiculo")).thenReturn(true);
        when(rs.getString("libreta")).thenReturn("A");
        when(rs.getString("ultimo_empleo")).thenReturn("Cajera");
        when(rs.getString("descripcion_experiencia")).thenReturn("Cinco años");
        when(rs.getString("cedula_identidad")).thenReturn("1.234.567-8");
        when(rs.getString("telefono")).thenReturn("099123456");
        return rs;
    }

    private static User usuario(Role rol, boolean activo) {
        User usuario = mock(User.class);
        when(usuario.getId()).thenReturn(1L);
        when(usuario.getRole()).thenReturn(rol);
        when(usuario.isActivo()).thenReturn(activo);
        return usuario;
    }

    private static UsernamePasswordAuthenticationToken auth() {
        return new UsernamePasswordAuthenticationToken("socio@example.test", null);
    }

    private static void assertStatus(HttpStatus esperado, Runnable accion) {
        assertThatThrownBy(accion::run)
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(error -> assertThat(((ResponseStatusException) error).getStatusCode()).isEqualTo(esperado));
    }
}

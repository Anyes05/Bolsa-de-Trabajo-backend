package uy.ccisj.api.notificacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.server.ResponseStatusException;
import uy.ccisj.api.comunicado.Comunicado;
import uy.ccisj.api.notificacion.dto.NotificacionResponseDTO;
import uy.ccisj.api.user.Role;
import uy.ccisj.api.user.User;
import uy.ccisj.api.user.UserRepository;

class NotificacionCoverageTest {
    private final NotificacionRepository repository = mock(NotificacionRepository.class);
    private final UserRepository usuarios = mock(UserRepository.class);
    private final NotificacionService service = new NotificacionService(repository, usuarios);

    @Test
    void controladoresDeleganConElRolCorrespondiente() {
        NotificacionService servicio = mock(NotificacionService.class);
        var auth = new UsernamePasswordAuthenticationToken("persona@x.test", null);
        NotificacionResponseDTO dto = new NotificacionResponseDTO(1L, "T", "C", true, false, null, null);
        when(servicio.list("persona@x.test", Role.POSTULANTE)).thenReturn(List.of(dto));
        when(servicio.list("persona@x.test", Role.SOCIO)).thenReturn(List.of());
        when(servicio.markOneRead("persona@x.test", 1L, Role.POSTULANTE)).thenReturn(dto);
        when(servicio.markOneRead("persona@x.test", 2L, Role.SOCIO)).thenReturn(dto);

        PostulanteNotificacionController postulante = new PostulanteNotificacionController(servicio);
        assertThat(postulante.list(auth)).containsExactly(dto);
        assertThat(postulante.markOneRead(auth, 1L)).isSameAs(dto);
        postulante.markAllRead(auth);
        postulante.dismissPopup(auth);

        SocioNotificacionController socio = new SocioNotificacionController(servicio);
        assertThat(socio.list(auth)).isEmpty();
        assertThat(socio.markOneRead(auth, 2L)).isSameAs(dto);
        socio.markAllRead(auth);
        socio.dismissPopup(auth);

        verify(servicio).markAllRead("persona@x.test", Role.POSTULANTE);
        verify(servicio).dismissPopups("persona@x.test", Role.POSTULANTE);
        verify(servicio).markAllRead("persona@x.test", Role.SOCIO);
        verify(servicio).dismissPopups("persona@x.test", Role.SOCIO);
    }

    @Test
    void descartaPopupsResolviendoUsuarioPorBpsOCedula() {
        User socio = usuario(5L, Role.SOCIO, true);
        when(usuarios.findByEmailIgnoreCase("12345")).thenReturn(Optional.empty());
        when(usuarios.findByBps("12345")).thenReturn(Optional.of(socio));
        User postulante = usuario(6L, Role.POSTULANTE, true);
        when(usuarios.findByEmailIgnoreCase("4.567.890-1")).thenReturn(Optional.empty());
        when(usuarios.findByBps("4.567.890-1")).thenReturn(Optional.empty());
        when(usuarios.findByCedula("4.567.890-1")).thenReturn(Optional.of(postulante));

        service.dismissPopups("12345", Role.SOCIO);
        service.dismissPopups("4.567.890-1", Role.POSTULANTE);

        verify(repository).dismissPopups(5L);
        verify(repository).dismissPopups(6L);
    }

    @Test
    void rechazaUsuarioInexistenteInactivoOConOtroRol() {
        when(usuarios.findByEmailIgnoreCase(any())).thenReturn(Optional.empty());
        when(usuarios.findByBps(any())).thenReturn(Optional.empty());
        when(usuarios.findByCedula(any())).thenReturn(Optional.empty());
        assertForbidden(() -> service.list("nadie@x.test", Role.SOCIO));

        User inactivo = usuario(7L, Role.SOCIO, false);
        when(usuarios.findByEmailIgnoreCase("inactivo@x.test")).thenReturn(Optional.of(inactivo));
        assertForbidden(() -> service.dismissPopups("inactivo@x.test", Role.SOCIO));

        User admin = usuario(8L, Role.ADMIN, true);
        when(usuarios.findByEmailIgnoreCase("admin@x.test")).thenReturn(Optional.of(admin));
        assertForbidden(() -> service.markAllRead("admin@x.test", Role.SOCIO));
        verify(repository, never()).dismissPopups(any());
        verify(repository, never()).markAllRead(any());
    }

    @Test
    void marcarLeidaApagaPopupYExponeElComunicadoAsociado() {
        User socio = usuario(5L, Role.SOCIO, true);
        when(usuarios.findByEmailIgnoreCase("socio@x.test")).thenReturn(Optional.of(socio));
        Comunicado comunicado = mock(Comunicado.class);
        when(comunicado.getId()).thenReturn(40L);
        Notificacion notificacion = new Notificacion(socio, comunicado, "Aviso", "Cuerpo", true);
        when(repository.findByIdAndUsuarioId(3L, 5L)).thenReturn(Optional.of(notificacion));
        when(repository.save(notificacion)).thenReturn(notificacion);

        NotificacionResponseDTO dto = service.markOneRead("socio@x.test", 3L, Role.SOCIO);

        assertThat(dto.comunicadoId()).isEqualTo(40L);
        assertThat(dto.leida()).isTrue();
        assertThat(dto.popup()).isFalse();
        assertThat(dto.titulo()).isEqualTo("Aviso");
        assertThat(dto.createdAt()).isNotNull();
        assertThat(notificacion.getUsuario()).isSameAs(socio);
        assertThat(notificacion.getComunicado()).isSameAs(comunicado);
    }

    @Test
    void constructorProtegidoInicializaFechaDeCreacion() {
        Notificacion vacia = new Notificacion();
        assertThat(vacia.getCreatedAt()).isNotNull();
        assertThat(vacia.getId()).isNull();
        assertThat(vacia.isLeida()).isFalse();
    }

    private static User usuario(Long id, Role rol, boolean activo) {
        User usuario = mock(User.class);
        when(usuario.getId()).thenReturn(id);
        when(usuario.getRole()).thenReturn(rol);
        when(usuario.isActivo()).thenReturn(activo);
        return usuario;
    }

    private static void assertForbidden(Runnable accion) {
        assertThatThrownBy(accion::run)
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(error -> assertThat(((ResponseStatusException) error).getStatusCode())
                        .isEqualTo(HttpStatus.FORBIDDEN));
    }
}

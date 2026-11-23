package uy.ccisj.api.comunicado;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.CallbackPreferringPlatformTransactionManager;
import org.springframework.web.server.ResponseStatusException;
import uy.ccisj.api.comunicado.dto.AudienciaCountsDTO;
import uy.ccisj.api.comunicado.dto.ComunicadoCreateDTO;
import uy.ccisj.api.comunicado.dto.ComunicadoResponseDTO;
import uy.ccisj.api.domain.DestinatarioComunicado;
import uy.ccisj.api.mail.MailNotificationService;
import uy.ccisj.api.notificacion.NotificacionRepository;
import uy.ccisj.api.socio.Socio;
import uy.ccisj.api.socio.SocioRepository;
import uy.ccisj.api.user.Role;
import uy.ccisj.api.user.User;
import uy.ccisj.api.user.UserRepository;

class ComunicadoCoverageTest {
    private final ComunicadoRepository comunicados = mock(ComunicadoRepository.class);
    private final NotificacionRepository notificaciones = mock(NotificacionRepository.class);
    private final SocioRepository socios = mock(SocioRepository.class);
    private final UserRepository usuarios = mock(UserRepository.class);
    private final MailNotificationService mail = mock(MailNotificationService.class);
    private final PlatformTransactionManager transacciones = mock(PlatformTransactionManager.class);
    private ComunicadoService service;

    @BeforeEach
    void setUp() {
        when(transacciones.getTransaction(any())).thenReturn(mock(TransactionStatus.class));
        when(comunicados.save(any(Comunicado.class))).thenAnswer(invocation -> invocation.getArgument(0));
        service = new ComunicadoService(comunicados, notificaciones, socios, usuarios, mail, transacciones);
    }

    @Test
    void transaccionSinResultadoDevuelveValoresVaciosOFallaAlPublicar() {
        // Un gestor "callback-preferring" simulado devuelve null desde execute().
        CallbackPreferringPlatformTransactionManager nulo = mock(CallbackPreferringPlatformTransactionManager.class);
        ComunicadoService sinResultado = new ComunicadoService(comunicados, notificaciones, socios, usuarios, mail, nulo);

        assertThat(sinResultado.audienciaCounts()).isEqualTo(new AudienciaCountsDTO(0, 0));
        assertThat(sinResultado.list()).isEmpty();
        assertThatThrownBy(() -> sinResultado.publish("admin@x.test",
                new ComunicadoCreateDTO("T", "C", DestinatarioComunicado.SOCIOS, false)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("No se pudo publicar el comunicado")
                .satisfies(error -> assertThat(((ResponseStatusException) error).getStatusCode())
                        .isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR));
        verify(mail, never()).send(anyString(), anyString(), any(uy.ccisj.api.mail.MailTemplate.Content.class));
    }

    @Test
    void listaComunicadoSinAutorConEmailNulo() {
        Comunicado sinAutor = new Comunicado(null, "Aviso", "Texto", DestinatarioComunicado.SOCIOS, false);
        when(comunicados.findAllByOrderByFechaPublicacionDesc()).thenReturn(List.of(sinAutor));

        ComunicadoResponseDTO dto = service.list().getFirst();

        assertThat(dto.autorEmail()).isNull();
        assertThat(dto.titulo()).isEqualTo("Aviso");
        assertThat(dto.correosDestino()).isEmpty();
    }

    @Test
    void publicaSoloADirectivosUsandoEmailDeCuentaCuandoContactoEsVacio() {
        when(usuarios.findByEmailIgnoreCase("admin@x.test")).thenReturn(Optional.of(user("admin@x.test", Role.ADMIN, true)));
        Socio contactoVacio = new Socio(user(" Cuenta@X.test ", Role.SOCIO, true), "Directiva 1");
        contactoVacio.setEmailContacto("   ");
        Socio emailVacio = new Socio(user("  ", Role.SOCIO, true), "Directiva 2");
        when(socios.findDirectivosActivosWithUsuario()).thenReturn(List.of(contactoVacio, emailVacio));

        ComunicadoResponseDTO response = service.publish("admin@x.test",
                new ComunicadoCreateDTO("Reunión", "Mañana", DestinatarioComunicado.SOLO_DIRECTIVOS, false));

        assertThat(response.destinatario()).isEqualTo(DestinatarioComunicado.SOLO_DIRECTIVOS);
        assertThat(response.correosDestino()).containsExactly("cuenta@x.test");
        assertThat(response.sociosSinEmail()).isEqualTo(1);
        assertThat(response.destinatariosNotificados()).isEqualTo(2);
        assertThat(response.correosEnviados()).isEqualTo(1);
        verify(socios, never()).findActivosWithUsuario();
    }

    @Test
    void resolverEmailDeContactoSinUsuarioDevuelveNulo() {
        Socio sinUsuario = new Socio(null, "Sin cuenta");
        assertThat(ComunicadoService.resolveContactEmail(sinUsuario)).isNull();
    }

    @Test
    void rechazaAdministradorInactivoYAudienciaDePostulantes() {
        when(usuarios.findByEmailIgnoreCase("inactivo@x.test"))
                .thenReturn(Optional.of(user("inactivo@x.test", Role.ADMIN, false)));
        assertThatThrownBy(() -> service.publish("inactivo@x.test",
                new ComunicadoCreateDTO("T", "C", DestinatarioComunicado.SOCIOS, false)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("administrador activa");

        when(usuarios.findByEmailIgnoreCase("admin@x.test")).thenReturn(Optional.of(user("admin@x.test", Role.ADMIN, true)));
        assertThatThrownBy(() -> service.publish("admin@x.test",
                new ComunicadoCreateDTO("T", "C", DestinatarioComunicado.POSTULANTES, false)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Todos los socios");
        verify(comunicados, never()).save(any());
    }

    @Test
    void entidadComunicadoPermiteModificarSusCampos() {
        User autor = user("a@x.test", Role.ADMIN, true);
        User otro = user("b@x.test", Role.ADMIN, true);
        Comunicado comunicado = new Comunicado(autor, "T", "C", DestinatarioComunicado.SOCIOS, false);

        comunicado.setAutor(otro);
        comunicado.setTitulo("Nuevo");
        comunicado.setContenido("Contenido nuevo");
        comunicado.setDestinatario(DestinatarioComunicado.SOLO_DIRECTIVOS);
        comunicado.setPopup(true);

        assertThat(comunicado.getAutor()).isSameAs(otro);
        assertThat(comunicado.getTitulo()).isEqualTo("Nuevo");
        assertThat(comunicado.getContenido()).isEqualTo("Contenido nuevo");
        assertThat(comunicado.getDestinatario()).isEqualTo(DestinatarioComunicado.SOLO_DIRECTIVOS);
        assertThat(comunicado.isPopup()).isTrue();
        assertThat(comunicado.getFechaPublicacion()).isNotNull();

        Comunicado vacio = new Comunicado();
        assertThat(vacio.getDestinatario()).isEqualTo(DestinatarioComunicado.TODOS);
        assertThat(vacio.getFechaPublicacion()).isNotNull();
    }

    @Test
    void controladorDelegaListadoAudienciaYPublicacion() {
        ComunicadoService servicio = mock(ComunicadoService.class);
        AdminComunicadoController controller = new AdminComunicadoController(servicio);
        ComunicadoCreateDTO dto = new ComunicadoCreateDTO("T", "C", DestinatarioComunicado.SOCIOS, true);
        ComunicadoResponseDTO respuesta = mock(ComunicadoResponseDTO.class);
        AudienciaCountsDTO counts = new AudienciaCountsDTO(10, 2);
        when(servicio.list()).thenReturn(List.of(respuesta));
        when(servicio.audienciaCounts()).thenReturn(counts);
        when(servicio.publish("admin@x.test", dto)).thenReturn(respuesta);

        assertThat(controller.list()).containsExactly(respuesta);
        assertThat(controller.audiencia()).isEqualTo(counts);
        assertThat(controller.publish(new UsernamePasswordAuthenticationToken("admin@x.test", null), dto))
                .isSameAs(respuesta);
    }

    private static User user(String email, Role rol, boolean activo) {
        User user = new User(email, null, "hash", rol);
        user.setActivo(activo);
        return user;
    }
}

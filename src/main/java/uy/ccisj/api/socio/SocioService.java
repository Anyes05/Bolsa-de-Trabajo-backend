package uy.ccisj.api.socio;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import uy.ccisj.api.catalogo.LocalidadesSanJose;
import uy.ccisj.api.catalogo.RubroEmpleo;
import uy.ccisj.api.catalogo.RubroEmpleoRepository;
import uy.ccisj.api.domain.EstadoMorosidad;
import uy.ccisj.api.auth.PasswordPolicy;
import uy.ccisj.api.auth.ProfilePhotoService;
import uy.ccisj.api.mail.MailNotificationService;
import uy.ccisj.api.mail.MailTemplate;
import uy.ccisj.api.socio.dto.CredencialesEnviadasDTO;
import uy.ccisj.api.socio.dto.DirectorioSocioDTO;
import uy.ccisj.api.socio.dto.RubroOptionDTO;
import uy.ccisj.api.socio.dto.SocioCreateDTO;
import uy.ccisj.api.socio.dto.SocioPerfilUpdateDTO;
import uy.ccisj.api.socio.dto.SocioResponseDTO;
import uy.ccisj.api.socio.dto.SocioUpdateDTO;
import uy.ccisj.api.socio.dto.TipoIdentificadorSocio;
import uy.ccisj.api.socio.importacion.CamposFicticios;
import uy.ccisj.api.user.Role;
import uy.ccisj.api.user.User;
import uy.ccisj.api.user.UserRepository;

@Service
public class SocioService {
    private static final Logger LOGGER = LoggerFactory.getLogger(SocioService.class);

    private final SocioRepository socioRepository;
    private final UserRepository userRepository;
    private final DireccionRepository direccionRepository;
    private final RubroEmpleoRepository rubroEmpleoRepository;
    private final PasswordEncoder passwordEncoder;
    private final CajaService cajaService;
    private final MailNotificationService mailNotificationService;
    private final String webUrl;
    private final SocioSubscriptionHistory subscriptionHistory;

    public SocioService(
            SocioRepository socioRepository,
            UserRepository userRepository,
            DireccionRepository direccionRepository,
            RubroEmpleoRepository rubroEmpleoRepository,
            PasswordEncoder passwordEncoder,
            CajaService cajaService,
            MailNotificationService mailNotificationService,
            @Value("${app.web-url:https://ccisj.cloud-ip.cc}") String webUrl,
            SocioSubscriptionHistory subscriptionHistory) {
        this.socioRepository = socioRepository;
        this.userRepository = userRepository;
        this.direccionRepository = direccionRepository;
        this.rubroEmpleoRepository = rubroEmpleoRepository;
        this.passwordEncoder = passwordEncoder;
        this.cajaService = cajaService;
        this.mailNotificationService = mailNotificationService;
        this.webUrl = webUrl;
        this.subscriptionHistory = subscriptionHistory;
    }

    @Transactional(readOnly = true)
    public List<SocioResponseDTO> getAllSocios() {
        return socioRepository.findAllByOrderByRazonSocialAsc().stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public SocioResponseDTO getSocioById(Long id) {
        return toResponse(findSocio(id));
    }

    @Transactional(readOnly = true)
    public List<RubroOptionDTO> listRubros() {
        return rubroEmpleoRepository.findByActivoTrueOrderByNombreRubroAsc().stream()
                .map(rubro -> new RubroOptionDTO(rubro.getId(), rubro.getNombreRubro()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<DirectorioSocioDTO> listDirectorio(String principalEmail) {
        Socio viewer = requireSocio(principalEmail);
        if (!viewer.isEsDirectivo()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "El directorio solo está disponible para socios directivos");
        }
        return socioRepository.findAllByOrderByRazonSocialAsc().stream()
                .filter(socio -> socio.getUsuario() != null && socio.getUsuario().isActivo())
                .map(this::toDirectorio)
                .toList();
    }

    @Transactional(readOnly = true)
    public SocioResponseDTO getMiPerfil(String principalEmail) {
        return toResponse(requireSocio(principalEmail));
    }

    @Transactional
    public SocioResponseDTO updateMiPerfil(String principalEmail, SocioPerfilUpdateDTO dto) {
        Socio socio = requireSocio(principalEmail);
        socio.setRazonSocial(dto.razonSocial().trim());
        socio.setTelefono(trimToNull(dto.telefono()));
        socio.setEmailContacto(normalizeEmail(dto.emailContacto()));
        assignRubro(socio, dto.rubroId());
        socio.setFechaAniversario(dto.fechaAniversario());
        Direccion direccion = socio.getDireccion();
        if (direccion == null) {
            direccion = new Direccion(dto.calle().trim(), trimToNull(dto.numero()), validateLocalidad(dto.localidad()));
        } else {
            direccion.setCalle(dto.calle().trim());
            direccion.setNumero(trimToNull(dto.numero()));
            direccion.setLocalidad(validateLocalidad(dto.localidad()));
        }
        socio.setDireccion(direccionRepository.save(direccion));
        return toResponse(socioRepository.save(socio));
    }

    private Socio requireSocio(String principalEmail) {
        User user = userRepository.findByEmailIgnoreCase(principalEmail)
                .filter(candidate -> candidate.isActivo() && candidate.getRole() == Role.SOCIO)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Se requiere una cuenta de socio activa"));
        return socioRepository.findByUsuarioId(user.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Socio no encontrado"));
    }

    private DirectorioSocioDTO toDirectorio(Socio socio) {
        User user = socio.getUsuario();
        RubroEmpleo rubro = socio.getRubro();
        String email = trimToNull(socio.getEmailContacto());
        if (email == null && user != null) {
            email = user.getEmail();
        }
        return new DirectorioSocioDTO(
                socio.getId(),
                socio.getRazonSocial(),
                rubro != null ? rubro.getNombreRubro() : "Sin rubro",
                socio.getTelefono(),
                email,
                resolveEstadoMorosidad(socio, user),
                socio.getFechaAlta(),
                socio.isEsDirectivo());
    }

    @Transactional
    public SocioResponseDTO createSocio(SocioCreateDTO dto) {
        TipoIdentificadorSocio tipo = dto.tipoIdentificador();
        String identificador = normalizeDigits(dto.identificador());
        if (identificador.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    tipo == TipoIdentificadorSocio.CEDULA
                            ? "El número de cédula es obligatorio"
                            : "El número de BPS es obligatorio");
        }
        if (tipo == TipoIdentificadorSocio.BPS && identificador.length() < 9) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "El número de BPS debe tener al menos 9 dígitos");
        }
        String email = trimToNull(dto.email());
        String rut = normalizeRut(dto.rut());
        if (tipo == TipoIdentificadorSocio.BPS) {
            if (userRepository.existsByBps(identificador)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Ya existe un socio con ese BPS");
            }
            if (userRepository.existsByCedula(identificador)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Ese número ya está registrado como cédula de otro socio");
            }
        } else {
            if (userRepository.existsByCedula(identificador)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Ya existe un socio con esa cédula");
            }
            if (userRepository.existsByBps(identificador)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Ese número ya está registrado como BPS de otro socio");
            }
        }
        if (email != null && userRepository.existsByEmailIgnoreCase(email)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ya existe una cuenta con ese correo");
        }
        if (socioRepository.existsByRutIgnoreCase(rut)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ya existe un socio con ese RUT o cédula");
        }

        String passwordProvisional = PasswordPolicy.generateProvisional(dto.razonSocial());
        User user = new User(
                email == null ? null : email.toLowerCase(),
                null,
                passwordEncoder.encode(passwordProvisional),
                Role.SOCIO);
        if (tipo == TipoIdentificadorSocio.BPS) {
            user.setBps(identificador);
            user.setCedula(null);
        } else {
            user.setBps(null);
            user.setCedula(identificador);
        }
        applyProvisionalPasswordWindow(user);
        userRepository.save(user);
        Direccion direccion = direccionRepository.save(new Direccion(
                dto.calle().trim(),
                trimToNull(dto.numero()),
                validateLocalidad(dto.localidad())));
        Socio socio = new Socio(user, dto.razonSocial().trim());
        socio.setRut(rut);
        socio.setTelefono(trimToNull(dto.telefono()));
        socio.setEmailContacto(normalizeEmail(dto.emailContacto()));
        socio.setDireccion(direccion);
        assignRubro(socio, dto.rubroId());
        socio.setEsDirectivo(dto.esDirectivo());
        socio.setFechaAlta(dto.fechaAlta() != null ? dto.fechaAlta() : LocalDate.now());
        socio.setFechaAniversario(dto.fechaAniversario());
        socio.setEstadoMorosidad(EstadoMorosidad.AL_DIA);
        Socio saved = socioRepository.save(socio);
        subscriptionHistory.record(saved.getId(), "ALTA", saved.getFechaAlta());
        CredencialesMailResult mailResult = trySendCredencialesEmail(saved, user, passwordProvisional);
        return toResponse(saved, passwordProvisional, mailResult.enviadas(), mailResult.mensaje());
    }

    @Transactional
    public SocioResponseDTO updateSocio(Long id, SocioUpdateDTO dto) {
        subscriptionHistory.lock(id);
        Socio socio = findSocio(id);
        User user = socio.getUsuario();
        java.util.Set<String> ficticios = CamposFicticios.parse(socio.getCamposFicticios());
        String rut = normalizeRut(dto.rut());
        if (socioRepository.existsByRutIgnoreCaseAndIdNot(rut, id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ya existe un socio con ese RUT o cédula");
        }
        clearIfChanged(ficticios, CamposFicticios.RUT, socio.getRut(), rut);
        socio.setRazonSocial(dto.razonSocial().trim());
        socio.setRut(rut);
        clearIfChanged(ficticios, CamposFicticios.TELEFONO, socio.getTelefono(), trimToNull(dto.telefono()));
        socio.setTelefono(trimToNull(dto.telefono()));
        String emailContacto = normalizeEmail(dto.emailContacto());
        clearIfChanged(ficticios, CamposFicticios.EMAIL_CONTACTO, socio.getEmailContacto(), emailContacto);
        socio.setEmailContacto(emailContacto);
        Long previousRubroId = socio.getRubro() == null ? null : socio.getRubro().getId();
        assignRubro(socio, dto.rubroId());
        if (previousRubroId != null && !previousRubroId.equals(dto.rubroId())) {
            ficticios.remove(CamposFicticios.RUBRO);
        }
        socio.setEsDirectivo(dto.esDirectivo());
        if (dto.fechaAlta() != null) {
            if (socio.getFechaAlta() == null || !socio.getFechaAlta().equals(dto.fechaAlta())) {
                ficticios.remove(CamposFicticios.FECHA_ALTA);
            }
            socio.setFechaAlta(dto.fechaAlta());
        }
        if (dto.fechaAniversario() != null && (socio.getFechaAniversario() == null
                || !socio.getFechaAniversario().equals(dto.fechaAniversario()))) {
            ficticios.remove(CamposFicticios.FECHA_ANIVERSARIO);
        }
        socio.setFechaAniversario(dto.fechaAniversario());
        if (dto.estadoMorosidad() != null) {
            applyEstadoMorosidad(socio, dto.estadoMorosidad());
        }
        Direccion direccion = socio.getDireccion();
        String nuevaCalle = dto.calle().trim();
        String nuevoNumero = trimToNull(dto.numero());
        String nuevaLocalidad = validateLocalidad(dto.localidad());
        if (direccion == null) {
            direccion = new Direccion(nuevaCalle, nuevoNumero, nuevaLocalidad);
        } else {
            clearIfChanged(ficticios, CamposFicticios.CALLE, direccion.getCalle(), nuevaCalle);
            clearIfChanged(ficticios, CamposFicticios.NUMERO, direccion.getNumero(), nuevoNumero);
            clearIfChanged(ficticios, CamposFicticios.LOCALIDAD, direccion.getLocalidad(), nuevaLocalidad);
            direccion.setCalle(nuevaCalle);
            direccion.setNumero(nuevoNumero);
            direccion.setLocalidad(nuevaLocalidad);
        }
        socio.setDireccion(direccionRepository.save(direccion));
        if (user != null && ficticios.contains(CamposFicticios.BPS) && dto.bps() != null && !dto.bps().isBlank()) {
            String bps = normalizeDigits(dto.bps());
            if (!bps.equals(user.getBps())) {
                if (bps.length() < 9) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El número de BPS debe tener al menos 9 dígitos");
                }
                if (userRepository.existsByBpsAndIdNot(bps, user.getId()) || userRepository.existsByCedula(bps)) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "Ya existe un socio con ese BPS");
                }
                user.setBps(bps);
                ficticios.remove(CamposFicticios.BPS);
                userRepository.save(user);
            }
        }
        if (user != null && ficticios.contains(CamposFicticios.EMAIL_USUARIO)
                && dto.emailUsuario() != null && !dto.emailUsuario().isBlank()) {
            String email = dto.emailUsuario().trim().toLowerCase();
            if (!email.equalsIgnoreCase(user.getEmail())) {
                if (userRepository.existsByEmailIgnoreCaseAndIdNot(email, user.getId())) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "Ya existe una cuenta con ese correo");
                }
                user.setEmail(email);
                ficticios.remove(CamposFicticios.EMAIL_USUARIO);
                userRepository.save(user);
            }
        }
        socio.setCamposFicticios(CamposFicticios.serialize(ficticios));
        return toResponse(socioRepository.save(socio));
    }

    @Transactional
    public SocioResponseDTO habilitarAcceso(Long id) {
        Socio socio = findSocio(id);
        User user = socio.getUsuario();
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El socio no tiene cuenta de acceso");
        }
        if (CamposFicticios.tiene(socio.getCamposFicticios(), CamposFicticios.BPS)
                || CamposFicticios.tiene(socio.getCamposFicticios(), CamposFicticios.EMAIL_USUARIO)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Completá el BPS y el email de cuenta reales antes de habilitar el acceso");
        }
        if (user.getEmail() == null || user.getEmail().endsWith("@incompleto.ccisj.local")) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "El socio necesita un email real para enviar las credenciales");
        }
        socio.setAccesoHabilitado(true);
        socioRepository.save(socio);
        String passwordProvisional = PasswordPolicy.generateProvisional(socio.getRazonSocial());
        user.setPasswordHash(passwordEncoder.encode(passwordProvisional));
        applyProvisionalPasswordWindow(user);
        user.setActivo(true);
        userRepository.save(user);
        CredencialesMailResult mailResult = trySendCredencialesEmail(socio, user, passwordProvisional);
        return toResponse(socio, passwordProvisional, mailResult.enviadas(), mailResult.mensaje());
    }

    @Transactional
    public SocioResponseDTO deactivateSocio(Long id) {
        subscriptionHistory.lock(id);
        Socio socio = findSocio(id);
        markInactive(socio);
        return toResponse(socioRepository.save(socio));
    }

    @Transactional
    public SocioResponseDTO activateSocio(Long id, LocalDate fecha) {
        subscriptionHistory.lock(id);
        Socio socio = findSocio(id);
        if (isActive(socio)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "El socio ya tiene una suscripción activa");
        }
        subscriptionHistory.validateReentry(id, fecha);
        if (fecha.isAfter(subscriptionHistory.today())) {
            subscriptionHistory.schedule(id, fecha);
            return toResponse(socio);
        }
        markActive(socio);
        subscriptionHistory.record(id, "REINGRESO", fecha);
        socioRepository.save(socio);
        cajaService.actualizarMorosidad(socio);
        return toResponse(socio);
    }

    @Transactional
    public void applyScheduledReentry(Long id) {
        subscriptionHistory.lock(id);
        Socio socio = findSocio(id);
        LocalDate fecha = subscriptionHistory.applyDue(id);
        if (fecha == null) return;
        if (isActive(socio)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "El socio ya está activo; revisar reingreso programado");
        }
        markActive(socio);
        subscriptionHistory.record(id, "REINGRESO", fecha);
        socioRepository.save(socio);
        cajaService.actualizarMorosidad(socio);
    }

    @Transactional
    public void cancelScheduledReentry(Long id) {
        subscriptionHistory.lock(id);
        subscriptionHistory.cancel(id);
    }

    @Transactional
    public SocioResponseDTO resetClaveProvisional(Long id) {
        Socio socio = findSocio(id);
        User user = socio.getUsuario();
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El socio no tiene cuenta de acceso");
        }
        if (!user.isActivo()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Reactiva al socio antes de restablecer la clave");
        }
        String passwordProvisional = PasswordPolicy.generateProvisional(socio.getRazonSocial());
        user.setPasswordHash(passwordEncoder.encode(passwordProvisional));
        applyProvisionalPasswordWindow(user);
        userRepository.save(user);
        return toResponse(socio, passwordProvisional);
    }

    @Transactional
    public CredencialesEnviadasDTO enviarCredenciales(Long id) {
        Socio socio = findSocio(id);
        User user = socio.getUsuario();
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El socio no tiene cuenta de acceso");
        }
        if (!user.isActivo()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Reactiva al socio antes de enviar las credenciales");
        }
        String destinatario = resolveEmailCredenciales(socio, user);
        if (destinatario == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "El socio no tiene email de contacto ni email de cuenta para enviarle las credenciales");
        }

        String passwordProvisional = PasswordPolicy.generateProvisional(socio.getRazonSocial());
        user.setPasswordHash(passwordEncoder.encode(passwordProvisional));
        applyProvisionalPasswordWindow(user);
        userRepository.save(user);

        mailNotificationService.requireConfigured();
        sendCredencialesEmail(socio, user, destinatario, passwordProvisional);
        return new CredencialesEnviadasDTO(
                destinatario,
                passwordProvisional,
                "Credenciales enviadas a " + destinatario);
    }

    /**
     * Best-effort: nunca aborta el alta. Si falla SMTP o no hay email, el socio queda creado.
     */
    private CredencialesMailResult trySendCredencialesEmail(Socio socio, User user, String passwordProvisional) {
        String destinatario = resolveEmailCredenciales(socio, user);
        if (destinatario == null) {
            return CredencialesMailResult.fallido(
                    "No se envió el mail: el socio no tiene email de contacto ni de cuenta.");
        }
        if (!mailNotificationService.isConfigured()) {
            return CredencialesMailResult.fallido(
                    "No se envió el mail: el servicio de correo no está configurado. Podés reenviarlo después.");
        }
        try {
            sendCredencialesEmail(socio, user, destinatario, passwordProvisional);
            return CredencialesMailResult.ok("Credenciales enviadas automáticamente a " + destinatario);
        } catch (Exception exception) {
            LOGGER.warn("No se pudieron enviar credenciales al socio id={}: {}",
                    socio.getId(), exception.getMessage());
            return CredencialesMailResult.fallido(
                    "El socio se creó, pero no se pudo enviar el mail (" + shortMessage(exception)
                            + "). Podés reenviarlo desde esta pantalla.");
        }
    }

    private void sendCredencialesEmail(Socio socio, User user, String destinatario, String passwordProvisional) {
        boolean conCedula = user.getCedula() != null && !user.getCedula().isBlank();
        String tipoUsuario = conCedula ? "Cédula" : "BPS";
        String usuarioAcceso = conCedula ? user.getCedula() : user.getBps();
        String subject = "CCISJ — Tus datos para ingresar a la Bolsa de Trabajo";
        var content = MailTemplate.build(
                "Bienvenida a la Bolsa de Trabajo",
                "Hola, " + (socio.getRazonSocial() == null ? "socio/a" : socio.getRazonSocial().trim()) + ":",
                List.of(
                        "Te creamos una cuenta en el sistema del Centro Comercial e Industrial de San José.",
                        "Usá los datos de abajo para ingresar. La contraseña es temporal: tenés 24 horas para entrar y elegir una definitiva.",
                        "La nueva contraseña debe tener al menos 8 caracteres, una mayúscula, una minúscula, un número y un carácter especial. Si se vence el plazo, pedile al administrador una nueva clave."),
                List.of(
                        new MailTemplate.Detail("Usuario (" + tipoUsuario + ")", usuarioAcceso),
                        new MailTemplate.Detail("Contraseña provisional", passwordProvisional),
                        new MailTemplate.Detail("Sitio web", webUrl)),
                new MailTemplate.Cta("Ingresar al sistema", webUrl),
                "Correo automático: no respondas a este mensaje.");
        mailNotificationService.send(destinatario, subject, content);
    }

    private String resolveEmailCredenciales(Socio socio, User user) {
        String destinatario = trimToNull(socio.getEmailContacto());
        if (destinatario == null && user != null) {
            destinatario = trimToNull(user.getEmail());
        }
        return destinatario;
    }

    private static String shortMessage(Exception exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) {
            return exception.getClass().getSimpleName();
        }
        return message.length() > 180 ? message.substring(0, 180) + "…" : message;
    }

    private record CredencialesMailResult(boolean enviadas, String mensaje) {
        static CredencialesMailResult ok(String mensaje) {
            return new CredencialesMailResult(true, mensaje);
        }

        static CredencialesMailResult fallido(String mensaje) {
            return new CredencialesMailResult(false, mensaje);
        }
    }

    private void applyProvisionalPasswordWindow(User user) {
        user.setDebeCambiarPassword(true);
        user.setPasswordProvisionalHasta(OffsetDateTime.now().plusHours(24));
    }

    private void applyEstadoMorosidad(Socio socio, EstadoMorosidad estado) {
        if (estado == EstadoMorosidad.INACTIVO) {
            markInactive(socio);
            return;
        }
        if (!isActive(socio)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Para reanudar la suscripción, ingresá la fecha en Historial de gestión de socios");
        }
        markActive(socio);
        socio.setEstadoMorosidad(estado);
    }

    private void markInactive(Socio socio) {
        boolean wasActive = isActive(socio);
        if (subscriptionHistory.hasPending(socio.getId())) {
            subscriptionHistory.cancel(socio.getId());
        }
        if (wasActive) subscriptionHistory.record(socio.getId(), "BAJA", subscriptionHistory.today());
        socio.setEstadoMorosidad(EstadoMorosidad.INACTIVO);
        User user = socio.getUsuario();
        if (user == null) {
            return;
        }

        user.setActivo(false);
        if (user.getFechaBaja() == null) {
            user.setFechaBaja(OffsetDateTime.now());
        }
        userRepository.save(user);
    }

    private boolean isActive(Socio socio) {
        return socio.getUsuario() != null && socio.getUsuario().isActivo()
                && socio.getEstadoMorosidad() != EstadoMorosidad.INACTIVO;
    }

    private void markActive(Socio socio) {
        User user = socio.getUsuario();
        if (user != null) {
            user.setActivo(true);
            user.setFechaBaja(null);
            userRepository.save(user);
        }
        if (socio.getEstadoMorosidad() == EstadoMorosidad.INACTIVO) {
            socio.setEstadoMorosidad(EstadoMorosidad.AL_DIA);
        }
    }

    private Socio findSocio(Long id) {
        return socioRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Socio no encontrado"));
    }

    private void assignRubro(Socio socio, Long rubroId) {
        RubroEmpleo rubro = findRubro(rubroId);
        socio.setRubro(rubro);
    }

    private RubroEmpleo findRubro(Long rubroId) {
        return rubroEmpleoRepository.findById(rubroId)
                .filter(RubroEmpleo::isActivo)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "El rubro no es válido"));
    }

    private SocioResponseDTO toResponse(Socio socio) {
        return toResponse(socio, null, null, null);
    }

    private SocioResponseDTO toResponse(Socio socio, String passwordProvisional) {
        return toResponse(socio, passwordProvisional, null, null);
    }

    private SocioResponseDTO toResponse(
            Socio socio,
            String passwordProvisional,
            Boolean credencialesEnviadas,
            String credencialesEnvioMensaje) {
        User user = socio.getUsuario();
        Direccion direccion = socio.getDireccion();
        RubroEmpleo rubro = socio.getRubro();
        EstadoMorosidad estado = resolveEstadoMorosidad(socio, user);
        String bps = user != null ? user.getBps() : null;
        String cedula = user != null ? user.getCedula() : null;
        TipoIdentificadorSocio tipo = resolveTipoIdentificador(bps, cedula);
        return new SocioResponseDTO(
                socio.getId(),
                bps,
                cedula,
                tipo,
                socio.getRazonSocial(),
                socio.getRut(),
                socio.getTelefono(),
                socio.getEmailContacto(),
                user != null ? user.getEmail() : null,
                direccion != null ? direccion.getCalle() : null,
                direccion != null ? direccion.getNumero() : null,
                direccion != null ? direccion.getLocalidad() : null,
                rubro != null ? rubro.getId() : null,
                rubro != null ? rubro.getNombreRubro() : null,
                estado,
                socio.isEsDirectivo(),
                socio.getFechaAlta(),
                user != null ? user.getFechaBaja() : null,
                socio.getFechaAniversario(),
                user == null || user.isActivo(),
                passwordProvisional,
                credencialesEnviadas,
                credencialesEnvioMensaje,
                user == null ? null : ProfilePhotoService.photoUrl(
                        user.getFotoPerfil(), user.getFotoMime(), user.getGooglePhotoUrl()),
                CamposFicticios.list(socio.getCamposFicticios()),
                socio.isImportadoMasivo(),
                socio.isAccesoHabilitado());
    }

    private TipoIdentificadorSocio resolveTipoIdentificador(String bps, String cedula) {
        if (cedula != null && !cedula.isBlank()) {
            return TipoIdentificadorSocio.CEDULA;
        }
        if (bps != null && !bps.isBlank()) {
            return TipoIdentificadorSocio.BPS;
        }
        return null;
    }

    private EstadoMorosidad resolveEstadoMorosidad(Socio socio, User user) {
        if (user != null && !user.isActivo()) {
            return EstadoMorosidad.INACTIVO;
        }
        return socio.getEstadoMorosidad();
    }

    private String normalizeDigits(String value) {
        return value == null ? "" : value.replaceAll("\\D", "");
    }

    private String normalizeRut(String value) {
        String rut = normalizeDigits(value);
        if (rut.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El RUT o la cédula es obligatorio");
        }
        if (rut.length() != 12 && rut.length() != 8) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Debe ser un RUT de 12 dígitos o una cédula de 8 dígitos");
        }
        return rut;
    }

    private String validateLocalidad(String value) {
        String localidad = value == null ? "" : value.trim();
        if (!LocalidadesSanJose.isValid(localidad)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "La localidad debe ser una de las localidades de San José precargadas");
        }
        return localidad;
    }

    private String normalizeEmail(String value) {
        String email = trimToNull(value);
        return email == null ? null : email.toLowerCase();
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private static void clearIfChanged(java.util.Set<String> ficticios, String campo, String anterior, String nuevo) {
        String left = anterior == null ? "" : anterior.trim();
        String right = nuevo == null ? "" : nuevo.trim();
        if (!left.equals(right)) {
            ficticios.remove(campo);
        }
    }
}

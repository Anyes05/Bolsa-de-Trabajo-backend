package uy.ccisj.api.socio;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import uy.ccisj.api.catalogo.RubroEmpleo;
import uy.ccisj.api.catalogo.RubroEmpleoRepository;
import uy.ccisj.api.domain.EstadoMorosidad;
import uy.ccisj.api.socio.dto.ClaveProvisionalDTO;
import uy.ccisj.api.socio.dto.DirectorioSocioDTO;
import uy.ccisj.api.socio.dto.RubroOptionDTO;
import uy.ccisj.api.socio.dto.SocioCreateDTO;
import uy.ccisj.api.socio.dto.SocioPerfilUpdateDTO;
import uy.ccisj.api.socio.dto.SocioResponseDTO;
import uy.ccisj.api.socio.dto.SocioUpdateDTO;
import uy.ccisj.api.socio.dto.TipoIdentificadorSocio;
import uy.ccisj.api.user.Role;
import uy.ccisj.api.user.User;
import uy.ccisj.api.user.UserRepository;

@Service
public class SocioService {
    private final SocioRepository socioRepository;
    private final UserRepository userRepository;
    private final DireccionRepository direccionRepository;
    private final RubroEmpleoRepository rubroEmpleoRepository;
    private final PasswordEncoder passwordEncoder;
    private final CajaService cajaService;

    public SocioService(
            SocioRepository socioRepository,
            UserRepository userRepository,
            DireccionRepository direccionRepository,
            RubroEmpleoRepository rubroEmpleoRepository,
            PasswordEncoder passwordEncoder,
            CajaService cajaService) {
        this.socioRepository = socioRepository;
        this.userRepository = userRepository;
        this.direccionRepository = direccionRepository;
        this.rubroEmpleoRepository = rubroEmpleoRepository;
        this.passwordEncoder = passwordEncoder;
        this.cajaService = cajaService;
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
            direccion = new Direccion(dto.calle().trim(), trimToNull(dto.numero()), dto.localidad().trim());
        } else {
            direccion.setCalle(dto.calle().trim());
            direccion.setNumero(trimToNull(dto.numero()));
            direccion.setLocalidad(dto.localidad().trim());
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
                rubro != null ? rubro.getNombreRubro() : (socio.getGiro() != null ? socio.getGiro() : "Sin rubro"),
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
        String email = trimToNull(dto.email());
        String rut = requireText(dto.rut(), "El RUT es obligatorio");
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
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ya existe un socio con ese RUT");
        }

        User user = new User(
                email == null ? null : email.toLowerCase(),
                null,
                passwordEncoder.encode(dto.password()),
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
                dto.localidad().trim()));
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
        return toResponse(socioRepository.save(socio));
    }

    @Transactional
    public SocioResponseDTO updateSocio(Long id, SocioUpdateDTO dto) {
        Socio socio = findSocio(id);
        String rut = requireText(dto.rut(), "El RUT es obligatorio");
        if (socioRepository.existsByRutIgnoreCaseAndIdNot(rut, id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ya existe un socio con ese RUT");
        }
        socio.setRazonSocial(dto.razonSocial().trim());
        socio.setRut(rut);
        socio.setTelefono(trimToNull(dto.telefono()));
        socio.setEmailContacto(normalizeEmail(dto.emailContacto()));
        assignRubro(socio, dto.rubroId());
        socio.setEsDirectivo(dto.esDirectivo());
        if (dto.fechaAlta() != null) {
            socio.setFechaAlta(dto.fechaAlta());
        }
        socio.setFechaAniversario(dto.fechaAniversario());
        if (dto.estadoMorosidad() != null) {
            applyEstadoMorosidad(socio, dto.estadoMorosidad());
        }
        Direccion direccion = socio.getDireccion();
        if (direccion == null) {
            direccion = new Direccion(dto.calle().trim(), trimToNull(dto.numero()), dto.localidad().trim());
        } else {
            direccion.setCalle(dto.calle().trim());
            direccion.setNumero(trimToNull(dto.numero()));
            direccion.setLocalidad(dto.localidad().trim());
        }
        socio.setDireccion(direccionRepository.save(direccion));
        return toResponse(socioRepository.save(socio));
    }

    @Transactional
    public SocioResponseDTO deactivateSocio(Long id) {
        Socio socio = findSocio(id);
        markInactive(socio);
        return toResponse(socioRepository.save(socio));
    }

    @Transactional
    public SocioResponseDTO activateSocio(Long id) {
        Socio socio = findSocio(id);
        markActive(socio);
        socioRepository.save(socio);
        cajaService.actualizarMorosidad(socio);
        return toResponse(socio);
    }

    @Transactional
    public SocioResponseDTO resetClaveProvisional(Long id, ClaveProvisionalDTO dto) {
        Socio socio = findSocio(id);
        User user = socio.getUsuario();
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El socio no tiene cuenta de acceso");
        }
        if (!user.isActivo()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Reactiva al socio antes de restablecer la clave");
        }
        user.setPasswordHash(passwordEncoder.encode(dto.password()));
        applyProvisionalPasswordWindow(user);
        userRepository.save(user);
        return toResponse(socio);
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
        markActive(socio);
        socio.setEstadoMorosidad(estado);
    }

    private void markInactive(Socio socio) {
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
        socio.setGiro(rubro.getNombreRubro());
    }

    private RubroEmpleo findRubro(Long rubroId) {
        return rubroEmpleoRepository.findById(rubroId)
                .filter(RubroEmpleo::isActivo)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "El rubro no es válido"));
    }

    private SocioResponseDTO toResponse(Socio socio) {
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
                socio.getGiro(),
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
                user == null || user.isActivo());
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

    private String normalizeEmail(String value) {
        String email = trimToNull(value);
        return email == null ? null : email.toLowerCase();
    }

    private String requireText(String value, String message) {
        String text = trimToNull(value);
        if (text == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
        }
        return text;
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}

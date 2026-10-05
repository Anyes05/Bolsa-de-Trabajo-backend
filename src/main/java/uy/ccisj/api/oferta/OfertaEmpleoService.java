package uy.ccisj.api.oferta;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import uy.ccisj.api.catalogo.RubroEmpleoRepository;
import uy.ccisj.api.catalogo.RubroEmpleo;
import uy.ccisj.api.domain.DisponibilidadHoraria;
import uy.ccisj.api.domain.EstadoOferta;
import uy.ccisj.api.socio.Socio;
import uy.ccisj.api.socio.SocioRepository;
import uy.ccisj.api.user.Role;
import uy.ccisj.api.user.User;
import uy.ccisj.api.user.UserRepository;

@Service
public class OfertaEmpleoService {
    private static final ZoneId APPLICATION_ZONE = ZoneId.of("America/Montevideo");

    private final OfertaEmpleoRepository ofertaRepository;
    private final SocioRepository socioRepository;
    private final RubroEmpleoRepository rubroRepository;
    private final UserRepository userRepository;

    public OfertaEmpleoService(OfertaEmpleoRepository ofertaRepository, SocioRepository socioRepository,
            RubroEmpleoRepository rubroRepository, UserRepository userRepository) {
        this.ofertaRepository = ofertaRepository;
        this.socioRepository = socioRepository;
        this.rubroRepository = rubroRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public List<OfertaResponse> list(String email, boolean admin) {
        expirePastOffers();
        List<OfertaEmpleo> offers = admin
                ? ofertaRepository.findAllByOrderByFechaPublicacionDesc()
                : ofertaRepository.findBySocioIdOrderByFechaPublicacionDesc(currentSocio(email).getId());
        return offers.stream().map(this::response).toList();
    }

    @Transactional
    public OfertaResponse create(String email, boolean admin, OfertaRequest request) {
        validate(request);
        Socio socio = admin ? findActiveSocio(request.socioId()) : currentSocio(email);
        var rubro = rubroRepository.findById(request.rubroId())
                .filter(RubroEmpleo::isActivo)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Rubro inválido"));
        var offer = new OfertaEmpleo(socio, rubro, request.titulo().trim(), request.descripcion().trim());
        apply(offer, request);
        return response(ofertaRepository.save(offer));
    }

    @Transactional
    public OfertaResponse update(String email, boolean admin, Long id, OfertaRequest request) {
        validate(request);
        expirePastOffers();
        OfertaEmpleo offer = findForRole(email, admin, id);
        if (offer.getEstado() == EstadoOferta.CERRADA || offer.getEstado() == EstadoOferta.VENCIDA) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "No se puede editar una oferta cerrada o vencida");
        }
        if (admin && request.socioId() != null) offer.setSocio(findActiveSocio(request.socioId()));
        var rubro = rubroRepository.findById(request.rubroId())
                .filter(RubroEmpleo::isActivo)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Rubro inválido"));
        offer.setRubro(rubro);
        offer.setTitulo(request.titulo().trim());
        offer.setDescripcion(request.descripcion().trim());
        apply(offer, request);
        return response(offer);
    }

    @Transactional
    public OfertaResponse setStatus(String email, boolean admin, Long id, EstadoOferta status) {
        OfertaEmpleo offer = findForRole(email, admin, id);
        if (offer.getEstado() == EstadoOferta.CERRADA || offer.getEstado() == EstadoOferta.VENCIDA) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "La oferta ya está cerrada");
        }
        if (status != EstadoOferta.PAUSADA && status != EstadoOferta.ACTIVA && status != EstadoOferta.CERRADA) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Estado de oferta inválido");
        }
        if (offer.getFechaCierre() != null && offer.getFechaCierre().isBefore(LocalDate.now(APPLICATION_ZONE))) {
            offer.setEstado(EstadoOferta.VENCIDA);
            return response(offer);
        }
        offer.setEstado(status);
        return response(offer);
    }

    public void expirePastOffers() {
        ofertaRepository.findByFechaCierreBeforeAndEstadoIn(LocalDate.now(APPLICATION_ZONE), List.of(EstadoOferta.ACTIVA, EstadoOferta.PAUSADA))
                .forEach(offer -> offer.setEstado(EstadoOferta.VENCIDA));
    }

    private void validate(OfertaRequest request) {
        if (request.titulo() == null || request.titulo().isBlank() || request.titulo().length() > 180) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El título es obligatorio y admite hasta 180 caracteres");
        }
        if (request.descripcion() == null || request.descripcion().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La descripción es obligatoria");
        }
        if (request.rubroId() == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Seleccioná un rubro");
        if (request.vacantes() == null || request.vacantes() < 1) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Debe haber al menos una vacante");
        if (request.fechaCierre() != null && request.fechaCierre().isBefore(LocalDate.now(APPLICATION_ZONE))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La fecha de cierre no puede estar en el pasado");
        }
    }

    private void apply(OfertaEmpleo offer, OfertaRequest request) {
        offer.setCargo(blankToNull(request.cargo()));
        offer.setZona(blankToNull(request.zona()));
        offer.setSalario(blankToNull(request.salario()));
        offer.setRequisitos(blankToNull(request.requisitos()));
        offer.setTipoContrato(request.tipoContrato());
        offer.setDisponibilidadHoraria(request.disponibilidadHoraria() == null ? DisponibilidadHoraria.FULL_TIME : request.disponibilidadHoraria());
        offer.setVacantes(request.vacantes());
        offer.setFechaCierre(request.fechaCierre());
    }

    private OfertaEmpleo findForRole(String email, boolean admin, Long id) {
        OfertaEmpleo offer = ofertaRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Oferta no encontrada"));
        if (!admin && !offer.getSocio().getId().equals(currentSocio(email).getId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Oferta no encontrada");
        }
        return offer;
    }

    private Socio currentSocio(String email) {
        User user = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Usuario no encontrado"));
        if (user.getRole() != Role.SOCIO) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Solo un socio puede operar sus ofertas");
        return socioRepository.findByUsuarioId(user.getId())
                .filter(socio -> socio.getUsuario().isActivo())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Socio no encontrado o inactivo"));
    }

    private Socio findActiveSocio(Long id) {
        if (id == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Seleccioná el socio de la oferta");
        return socioRepository.findById(id)
                .filter(socio -> socio.getUsuario().isActivo())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "El socio seleccionado no está activo"));
    }

    private OfertaResponse response(OfertaEmpleo offer) {
        return new OfertaResponse(offer.getId(), offer.getSocio().getId(), offer.getSocio().getRazonSocial(),
                offer.getRubro().getId(), offer.getRubro().getNombreRubro(), offer.getTitulo(), offer.getCargo(),
                offer.getDescripcion(), offer.getRequisitos(), offer.getZona(), offer.getSalario(), offer.getTipoContrato(),
                offer.getDisponibilidadHoraria(), offer.getVacantes(), offer.getFechaPublicacion(), offer.getFechaCierre(),
                offer.getEstado(), offer.getPostulaciones().size());
    }

    private String blankToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }
}
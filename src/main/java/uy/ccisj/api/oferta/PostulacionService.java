package uy.ccisj.api.oferta;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import uy.ccisj.api.domain.EstadoOferta;
import uy.ccisj.api.postulante.CvRepository;
import uy.ccisj.api.postulante.PerfilLaboral;
import uy.ccisj.api.postulante.PostulanteRepository;

@Service
public class PostulacionService {
    private static final ZoneId APPLICATION_ZONE = ZoneId.of("America/Montevideo");

    private final OfertaEmpleoRepository ofertaRepository;
    private final PostulanteRepository postulanteRepository;
    private final PostulacionRepository postulacionRepository;
    private final CvRepository cvRepository;
    private final OfertaEmpleoService ofertaService;

    public PostulacionService(OfertaEmpleoRepository ofertaRepository, PostulanteRepository postulanteRepository,
            PostulacionRepository postulacionRepository, CvRepository cvRepository, OfertaEmpleoService ofertaService) {
        this.ofertaRepository = ofertaRepository;
        this.postulanteRepository = postulanteRepository;
        this.postulacionRepository = postulacionRepository;
        this.cvRepository = cvRepository;
        this.ofertaService = ofertaService;
    }

    public List<OfertaResponse> listOffers(Long rubroId) {
        return ofertaService.listForApplicants(rubroId);
    }

    @Transactional(readOnly = true)
    public List<PostulacionResponse> listMine(String email) {
        var postulante = requirePostulante(email);
        return postulacionRepository.findByPostulanteIdOrderByFechaDesc(postulante.getId()).stream()
                .map(this::response)
                .toList();
    }

    @Transactional
    public PostulacionResponse apply(String email, Long ofertaId, Long perfilId) {
        var postulante = requirePostulante(email);
        var offer = ofertaRepository.findById(ofertaId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Oferta no encontrada"));
        if (offer.getEstado() != EstadoOferta.ACTIVA
                || (offer.getFechaCierre() != null && offer.getFechaCierre().isBefore(LocalDate.now(APPLICATION_ZONE)))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "La oferta ya no recibe postulaciones");
        }
        if (postulacionRepository.existsByPostulanteIdAndOfertaId(postulante.getId(), ofertaId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ya te postulaste a esta oferta");
        }
        PerfilLaboral profile = postulante.getPerfiles().stream()
                .filter(candidate -> candidate.getId().equals(perfilId) && candidate.isVisible())
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Seleccioná uno de tus perfiles visibles"));
        var cv = cvRepository.findByPostulanteIdAndPerfilLaboralIdAndActivoTrue(postulante.getId(), profile.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "El perfil seleccionado necesita un CV activo asociado"));

        Postulacion application = new Postulacion(postulante, offer);
        application.setPerfilLaboral(profile);
        application.setCv(cv);
        application.setEstado(EstadoPostulacion.RECIBIDA);
        return response(postulacionRepository.save(application));
    }

    private uy.ccisj.api.postulante.Postulante requirePostulante(String email) {
        return postulanteRepository.findByUsuarioEmailIgnoreCase(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "Perfil de postulante no encontrado"));
    }

    private PostulacionResponse response(Postulacion application) {
        OfertaEmpleo offer = application.getOferta();
        return new PostulacionResponse(application.getId(), offer.getId(), offer.getTitulo(),
                offer.getSocio().getRazonSocial(), offer.getRubro().getNombreRubro(), application.getFecha(),
                application.getEstado(), application.getPerfilLaboral() == null ? null : application.getPerfilLaboral().getId(),
                application.getPerfilLaboral() == null ? null : application.getPerfilLaboral().getNombre(),
                application.getCv() == null ? null : application.getCv().getId(),
                application.getCv() == null ? null : application.getCv().getNombreArchivo());
    }

    public record PostulacionResponse(Long id, Long ofertaId, String ofertaTitulo, String socioNombre,
            String rubro, java.time.OffsetDateTime fecha, EstadoPostulacion estado,
            Long perfilId, String perfilNombre, Long cvId, String cvNombre) {}
}
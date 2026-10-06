package uy.ccisj.api.oferta;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PostulacionRepository extends JpaRepository<Postulacion, Long> {
    boolean existsByPostulanteIdAndOfertaId(Long postulanteId, Long ofertaId);

    @EntityGraph(attributePaths = {"oferta", "oferta.socio", "oferta.rubro", "perfilLaboral", "cv"})
    List<Postulacion> findByPostulanteIdOrderByFechaDesc(Long postulanteId);

    @EntityGraph(attributePaths = {"postulante", "perfilLaboral", "cv", "oferta", "oferta.rubro"})
    List<Postulacion> findByOfertaIdAndOfertaSocioIdOrderByFechaDesc(Long ofertaId, Long socioId);

    @EntityGraph(attributePaths = {"cv", "oferta", "oferta.socio"})
    Optional<Postulacion> findByIdAndOfertaSocioId(Long id, Long socioId);
}
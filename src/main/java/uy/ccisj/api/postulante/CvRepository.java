package uy.ccisj.api.postulante;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CvRepository extends JpaRepository<Cv, Long> {
    List<Cv> findByPostulanteIdOrderByVersionDesc(Long postulanteId);

    List<Cv> findByPostulanteIdAndPerfilLaboralIdOrderByVersionDesc(Long postulanteId, Long perfilLaboralId);

    Optional<Cv> findByIdAndPostulanteId(Long id, Long postulanteId);

    Optional<Cv> findByPostulanteIdAndActivoTrue(Long postulanteId);

    Optional<Cv> findByPostulanteIdAndPerfilLaboralIdAndActivoTrue(Long postulanteId, Long perfilLaboralId);

    long countByPostulanteId(Long postulanteId);
}

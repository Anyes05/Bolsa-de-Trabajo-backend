package uy.ccisj.api.postulante;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PostulanteRepository extends JpaRepository<Postulante, Long> {
    Optional<Postulante> findByUsuarioEmailIgnoreCase(String email);
}

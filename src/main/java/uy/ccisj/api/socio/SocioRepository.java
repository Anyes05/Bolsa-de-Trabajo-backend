package uy.ccisj.api.socio;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SocioRepository extends JpaRepository<Socio, Long> {
    Optional<Socio> findByUsuarioId(Long usuarioId);
}

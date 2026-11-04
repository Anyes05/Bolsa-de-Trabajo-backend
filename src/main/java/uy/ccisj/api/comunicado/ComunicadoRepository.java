package uy.ccisj.api.comunicado;

import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ComunicadoRepository extends JpaRepository<Comunicado, Long> {
    @EntityGraph(attributePaths = {"autor"})
    List<Comunicado> findAllByOrderByFechaPublicacionDesc();
}

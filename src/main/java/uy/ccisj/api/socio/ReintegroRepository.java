package uy.ccisj.api.socio;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReintegroRepository extends JpaRepository<Reintegro, Long> {
    List<Reintegro> findBySocioIdOrderByFechaAscIdAsc(Long socioId);

    boolean existsByOrigen(String origen);
}
package uy.ccisj.api.socio;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReintegroProgramadoRepository extends JpaRepository<ReintegroProgramado, Long> {
    List<ReintegroProgramado> findBySocioIdOrderByIdAsc(Long socioId);
}
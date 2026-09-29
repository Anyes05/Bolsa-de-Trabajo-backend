package uy.ccisj.api.socio;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CuotaRepository extends JpaRepository<Cuota, Long> {
    @EntityGraph(attributePaths = {"socio", "socio.usuario", "socio.rubro", "pago"})
    List<Cuota> findAllByOrderByPeriodoDesc();

    @EntityGraph(attributePaths = {"socio", "socio.usuario", "socio.rubro", "pago"})
    List<Cuota> findBySocioIdOrderByPeriodoDesc(Long socioId);

    Optional<Cuota> findFirstBySocioIdOrderByPeriodoDesc(Long socioId);

    boolean existsBySocioIdAndPeriodo(Long socioId, java.time.LocalDate periodo);

    boolean existsByTarifaId(Long tarifaId);
}
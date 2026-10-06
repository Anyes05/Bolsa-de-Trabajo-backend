package uy.ccisj.api.oferta;

import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OfertaEmpleoRepository extends JpaRepository<OfertaEmpleo, Long> {
    List<OfertaEmpleo> findAllByOrderByFechaPublicacionDesc();
    List<OfertaEmpleo> findBySocioIdOrderByFechaPublicacionDesc(Long socioId);
    List<OfertaEmpleo> findByEstadoOrderByFechaPublicacionDesc(uy.ccisj.api.domain.EstadoOferta estado);
    List<OfertaEmpleo> findByFechaCierreBeforeAndEstadoIn(LocalDate date, List<uy.ccisj.api.domain.EstadoOferta> states);
}
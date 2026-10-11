package uy.ccisj.api.socio;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PagoRepository extends JpaRepository<Pago, Long> {
	List<Pago> findBySocioIdOrderByFechaEmisionAscIdAsc(Long socioId);
}
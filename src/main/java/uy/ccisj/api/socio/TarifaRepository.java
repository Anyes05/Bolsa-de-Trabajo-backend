package uy.ccisj.api.socio;

import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

public interface TarifaRepository extends JpaRepository<Tarifa, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Tarifa> findByAnio(int anio);

    List<Tarifa> findAllByOrderByAnioDesc();
}
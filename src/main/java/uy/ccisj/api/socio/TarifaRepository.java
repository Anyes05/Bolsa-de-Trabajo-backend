package uy.ccisj.api.socio;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TarifaRepository extends JpaRepository<Tarifa, Long> {
    Optional<Tarifa> findByAnio(int anio);

    List<Tarifa> findAllByOrderByAnioDesc();
}
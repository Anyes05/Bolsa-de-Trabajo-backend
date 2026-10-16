package uy.ccisj.api.catalogo;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RubroEmpleoRepository extends JpaRepository<RubroEmpleo, Long> {
    List<RubroEmpleo> findByActivoTrueOrderByNombreRubroAsc();

    List<RubroEmpleo> findAllByOrderByNombreRubroAsc();

    boolean existsByNombreRubroIgnoreCase(String nombreRubro);

    boolean existsByNombreRubroIgnoreCaseAndIdNot(String nombreRubro, Long id);
}

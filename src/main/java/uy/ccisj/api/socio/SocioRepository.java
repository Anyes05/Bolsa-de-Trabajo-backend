package uy.ccisj.api.socio;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SocioRepository extends JpaRepository<Socio, Long> {
    @EntityGraph(attributePaths = {"usuario", "direccion", "rubro"})
    List<Socio> findAllByOrderByRazonSocialAsc();

    @EntityGraph(attributePaths = {"usuario", "direccion", "rubro"})
    @Override
    Optional<Socio> findById(Long id);

    boolean existsByRutIgnoreCase(String rut);

    boolean existsByRutIgnoreCaseAndIdNot(String rut, Long id);

    Optional<Socio> findByUsuarioId(Long usuarioId);
}

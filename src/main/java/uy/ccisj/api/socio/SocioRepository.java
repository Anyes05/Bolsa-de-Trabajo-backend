package uy.ccisj.api.socio;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import uy.ccisj.api.domain.EstadoMorosidad;

public interface SocioRepository extends JpaRepository<Socio, Long> {
    @EntityGraph(attributePaths = {"usuario", "direccion", "rubro"})
    List<Socio> findAllByOrderByRazonSocialAsc();

    @EntityGraph(attributePaths = {"usuario", "direccion", "rubro"})
    @Override
    Optional<Socio> findById(Long id);

    boolean existsByRutIgnoreCase(String rut);

    boolean existsByRutIgnoreCaseAndIdNot(String rut, Long id);

    Optional<Socio> findByUsuarioId(Long usuarioId);

    @EntityGraph(attributePaths = {"usuario"})
    @Query("""
            select s from Socio s
            join s.usuario u
            where u.activo = true
              and s.estadoMorosidad <> uy.ccisj.api.domain.EstadoMorosidad.INACTIVO
            order by s.razonSocial asc
            """)
    List<Socio> findActivosWithUsuario();

    @EntityGraph(attributePaths = {"usuario"})
    @Query("""
            select s from Socio s
            join s.usuario u
            where u.activo = true
              and s.estadoMorosidad <> uy.ccisj.api.domain.EstadoMorosidad.INACTIVO
              and s.esDirectivo = true
            order by s.razonSocial asc
            """)
    List<Socio> findDirectivosActivosWithUsuario();

    @Query("""
            select count(s) from Socio s
            join s.usuario u
            where u.activo = true
              and s.estadoMorosidad <> :inactivo
            """)
    long countActivos(EstadoMorosidad inactivo);

    @Query("""
            select count(s) from Socio s
            join s.usuario u
            where u.activo = true
              and s.estadoMorosidad <> :inactivo
              and s.esDirectivo = true
            """)
    long countDirectivosActivos(EstadoMorosidad inactivo);
}

package uy.ccisj.api.postulante;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PostulanteRepository extends JpaRepository<Postulante, Long> {
    Optional<Postulante> findByUsuarioEmailIgnoreCase(String email);
    Optional<Postulante> findByUsuarioId(Long usuarioId);
    Optional<Postulante> findByRetencionToken(String retencionToken);

    @EntityGraph(attributePaths = {"usuario"})
    @Query("""
            select p from Postulante p
            join p.usuario u
            where u.activo = true
              and p.visible = true
              and p.retencionAvisadoAt is null
              and p.ultimaActividadAt < :limite
            order by p.ultimaActividadAt asc
            """)
    List<Postulante> findPendientesDeAviso(@Param("limite") OffsetDateTime limite);

    @EntityGraph(attributePaths = {"usuario"})
    @Query("""
            select p from Postulante p
            join p.usuario u
            where u.activo = true
              and p.visible = true
              and p.retencionDeadlineAt is not null
              and p.retencionDeadlineAt < :ahora
            order by p.retencionDeadlineAt asc
            """)
    List<Postulante> findPendientesDeOcultar(@Param("ahora") OffsetDateTime ahora);
}

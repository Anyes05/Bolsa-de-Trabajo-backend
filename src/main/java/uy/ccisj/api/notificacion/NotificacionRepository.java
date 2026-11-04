package uy.ccisj.api.notificacion;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificacionRepository extends JpaRepository<Notificacion, Long> {
    List<Notificacion> findByUsuarioIdOrderByCreatedAtDesc(Long usuarioId);

    long countByUsuarioIdAndLeidaFalse(Long usuarioId);

    Optional<Notificacion> findByIdAndUsuarioId(Long id, Long usuarioId);

    @Modifying(clearAutomatically = true)
    @Query("update Notificacion n set n.leida = true where n.usuario.id = :usuarioId and n.leida = false")
    int markAllRead(@Param("usuarioId") Long usuarioId);

    @Modifying(clearAutomatically = true)
    @Query("update Notificacion n set n.popup = false where n.usuario.id = :usuarioId and n.popup = true")
    int dismissPopups(@Param("usuarioId") Long usuarioId);
}

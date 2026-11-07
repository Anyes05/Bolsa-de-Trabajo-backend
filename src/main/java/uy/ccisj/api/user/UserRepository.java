package uy.ccisj.api.user;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmailIgnoreCase(String email);
    Optional<User> findByBps(String bps);
    Optional<User> findByCedula(String cedula);
    boolean existsByEmailIgnoreCase(String email);
    boolean existsByBps(String bps);
    boolean existsByCedula(String cedula);
}

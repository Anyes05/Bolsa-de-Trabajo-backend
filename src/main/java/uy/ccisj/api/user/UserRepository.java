package uy.ccisj.api.user;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmailIgnoreCase(String email);
    Optional<User> findByBps(String bps);
    Optional<User> findByCedula(String cedula);
    Optional<User> findByGoogleSubject(String googleSubject);
    boolean existsByEmailIgnoreCase(String email);
    boolean existsByEmailIgnoreCaseAndIdNot(String email, Long id);
    boolean existsByBps(String bps);
    boolean existsByBpsAndIdNot(String bps, Long id);
    boolean existsByCedula(String cedula);
}

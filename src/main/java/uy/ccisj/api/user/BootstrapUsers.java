package uy.ccisj.api.user;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class BootstrapUsers implements CommandLineRunner {
    private final JdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder;
    private final String adminEmail;
    private final String adminPassword;

    public BootstrapUsers(
            JdbcTemplate jdbcTemplate,
            PasswordEncoder passwordEncoder,
            @Value("${app.bootstrap.admin-email}") String adminEmail,
            @Value("${app.bootstrap.admin-password}") String adminPassword) {
        this.jdbcTemplate = jdbcTemplate;
        this.passwordEncoder = passwordEncoder;
        this.adminEmail = adminEmail;
        this.adminPassword = adminPassword;
    }

    @Override
    public void run(String... args) {
        createAdminIfConfigured();
    }

    private void createAdminIfConfigured() {
        if (!adminEmail.isBlank() && !adminPassword.isBlank()) {
            jdbcTemplate.update("INSERT INTO usuarios (email, password_hash, rol) VALUES (?, ?, 'ADMIN') ON CONFLICT (email) DO NOTHING",
                    adminEmail.trim().toLowerCase(), passwordEncoder.encode(adminPassword));
        }
    }

}
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
    private final String memberEmail;
    private final String memberBps;
    private final String memberPassword;

    public BootstrapUsers(
            JdbcTemplate jdbcTemplate,
            PasswordEncoder passwordEncoder,
            @Value("${app.bootstrap.admin-email}") String adminEmail,
            @Value("${app.bootstrap.admin-password}") String adminPassword,
            @Value("${app.bootstrap.member-email}") String memberEmail,
            @Value("${app.bootstrap.member-bps}") String memberBps,
            @Value("${app.bootstrap.member-password}") String memberPassword) {
        this.jdbcTemplate = jdbcTemplate;
        this.passwordEncoder = passwordEncoder;
        this.adminEmail = adminEmail;
        this.adminPassword = adminPassword;
        this.memberEmail = memberEmail;
        this.memberBps = memberBps;
        this.memberPassword = memberPassword;
    }

    @Override
    public void run(String... args) {
        createAdminIfConfigured();
        createMemberIfConfigured();
    }

    private void createAdminIfConfigured() {
        if (!adminEmail.isBlank() && !adminPassword.isBlank()) {
            jdbcTemplate.update("INSERT INTO usuarios (email, password_hash, rol) VALUES (?, ?, 'ADMIN') ON CONFLICT (email) DO NOTHING",
                    adminEmail.trim().toLowerCase(), passwordEncoder.encode(adminPassword));
        }
    }

    private void createMemberIfConfigured() {
        if (!memberEmail.isBlank() && !memberBps.isBlank() && !memberPassword.isBlank()) {
            jdbcTemplate.update("INSERT INTO usuarios (email, bps, password_hash, rol) VALUES (?, ?, ?, 'SOCIO') ON CONFLICT DO NOTHING",
                    memberEmail.trim().toLowerCase(), memberBps.trim(), passwordEncoder.encode(memberPassword));
        }
    }
}
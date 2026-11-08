package uy.ccisj.api.auth;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import uy.ccisj.api.postulante.RetencionPermanenciaService;
import uy.ccisj.api.user.Role;
import uy.ccisj.api.user.User;
import uy.ccisj.api.user.UserRepository;

@Service
public class GoogleAuthService {
    private final GoogleTokenVerifier verifier;
    private final UserRepository users;
    private final AuthService auth;
    private final JwtService jwt;
    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwords;
    private final RetencionPermanenciaService retencionPermanenciaService;

    public GoogleAuthService(GoogleTokenVerifier verifier, UserRepository users, AuthService auth,
            JwtService jwt, JdbcTemplate jdbc, PasswordEncoder passwords,
            RetencionPermanenciaService retencionPermanenciaService) {
        this.verifier = verifier;
        this.users = users;
        this.auth = auth;
        this.jwt = jwt;
        this.jdbc = jdbc;
        this.passwords = passwords;
        this.retencionPermanenciaService = retencionPermanenciaService;
    }

    @Transactional
    public GoogleAuthResult authenticate(String credential, String nonce, String password) {
        var identity = verifier.verify(credential, nonce);
        var linked = users.findByGoogleSubject(identity.subject());
        if (linked.isPresent()) return login(linked.get());
        var existing = users.findByEmailIgnoreCase(identity.email());
        if (existing.isPresent()) {
            User user = existing.get();
            requireApplicant(user);
            if (user.getGoogleSubject() != null) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Esta cuenta ya esta vinculada a otra identidad de Google");
            }
            if (!identity.authoritative() && password == null) {
                return new GoogleAuthResult("LINK_REQUIRED", null, null, identity.fullName(), identity.email(), null);
            }
            if (password != null && !passwords.matches(password, user.getPasswordHash())) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "La contraseña actual no es correcta");
            }
            user.setGoogleSubject(identity.subject());
            users.saveAndFlush(user);
            jdbc.update("UPDATE usuarios SET google_photo_url = ? WHERE id = ? AND foto_perfil IS NULL",
                    identity.fotoUrl(), user.getId());
            return login(user);
        }
        return new GoogleAuthResult("REGISTER", null, jwt.googleRegistrationToken(identity),
                identity.fullName(), identity.email(), identity.fotoUrl());
    }

    @Transactional
    public AuthResponse register(GoogleRegistrationRequest request) {
        var identity = jwt.googleRegistrationIdentity(request.registrationToken());
        if (users.findByGoogleSubject(identity.subject()).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Esta cuenta de Google ya esta registrada. Inicia sesion.");
        }
        return auth.registerGoogleApplicant(request, identity);
    }

    private GoogleAuthResult login(User user) {
        requireApplicant(user);
        retencionPermanenciaService.touchActividad(user);
        return new GoogleAuthResult("LOGIN", auth.authResponse(user), null, null, null, null);
    }

    private void requireApplicant(User user) {
        if (!user.isActivo() || user.getRole() != Role.POSTULANTE) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Google solo esta disponible para cuentas de postulante activas");
        }
    }

    public record GoogleAuthResult(String status, AuthResponse session, String registrationToken,
            String fullName, String email, String fotoUrl) {}
}

package uy.ccisj.api.auth;

import java.util.Collections;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import uy.ccisj.api.postulante.PostulanteRepository;
import uy.ccisj.api.user.Role;
import uy.ccisj.api.user.User;
import uy.ccisj.api.user.UserRepository;

@Service
public class AuthService {
    private final UserRepository userRepository;
    private final PostulanteRepository postulanteRepository;
    private final JdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(
            UserRepository userRepository,
            PostulanteRepository postulanteRepository,
            JdbcTemplate jdbcTemplate,
            PasswordEncoder passwordEncoder,
            JwtService jwtService) {
        this.userRepository = userRepository;
        this.postulanteRepository = postulanteRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    public AuthResponse login(LoginRequest request) {
        User user = (request.role() == Role.SOCIO
                ? userRepository.findByBps(request.identifier())
                : userRepository.findByEmailIgnoreCase(request.identifier()))
                .filter(User::isActivo)
                .filter(candidate -> candidate.getRole() == request.role())
                .filter(candidate -> passwordEncoder.matches(request.password(), candidate.getPasswordHash()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Credenciales invalidas"));
        return authResponse(user);
    }

    @Transactional
    public AuthResponse registerApplicant(RegisterApplicantRequest request) {
        if (userRepository.existsByEmailIgnoreCase(request.email())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ya existe una cuenta con ese correo");
        }

        Long userId = insertAndReturnId("INSERT INTO usuarios (email, password_hash, rol) VALUES (?, ?, 'POSTULANTE')",
                request.email().trim().toLowerCase(), passwordEncoder.encode(request.password()));
        Long applicantId = insertAndReturnId("INSERT INTO postulantes (usuario_id, nombre_completo, cedula_identidad, telefono, zona_residencia) VALUES (?, ?, ?, ?, ?)",
                userId, request.fullName(), request.identityCard(), request.phone(), request.residenceArea());
        Long profileId = insertAndReturnId("INSERT INTO perfiles_laborales (postulante_id, nombre, disponibilidad_horaria, tiene_vehiculo, ultimo_empleo, descripcion_experiencia) VALUES (?, ?, ?, ?, ?, ?)",
                applicantId, request.profileName(), request.availability(), request.hasVehicle(), request.latestJob(), request.experienceDescription());

        String placeholders = String.join(",", Collections.nCopies(request.sectors().size(), "?"));
        List<Long> sectorIds = jdbcTemplate.queryForList(
            "SELECT id FROM rubros_empleo WHERE activo = true AND nombre_rubro IN (" + placeholders + ")",
            Long.class,
            request.sectors().toArray());
        if (sectorIds.size() != request.sectors().stream().distinct().count()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Uno o mas rubros no son validos");
        }
        sectorIds.forEach(sectorId -> jdbcTemplate.update("INSERT INTO perfil_rubros (perfil_id, rubro_id) VALUES (?, ?)", profileId, sectorId));

        User user = userRepository.findById(userId).orElseThrow();
        return new AuthResponse(jwtService.generate(user), user.getEmail(), user.getRole(), request.fullName());
    }

    private AuthResponse authResponse(User user) {
        String fullName = applicantFullName(user);
        return new AuthResponse(jwtService.generate(user), user.getEmail(), user.getRole(), fullName);
    }

        public String applicantFullName(String email) {
        return userRepository.findByEmailIgnoreCase(email)
            .map(this::applicantFullName)
            .orElse(null);
        }

        private String applicantFullName(User user) {
        return user.getRole() == Role.POSTULANTE
            ? postulanteRepository.findByUsuarioId(user.getId())
                .map(postulante -> postulante.getNombreCompleto())
                .orElse(null)
            : null;
        }

    private Long insertAndReturnId(String sql, Object... values) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            var statement = connection.prepareStatement(sql, new String[] {"id"});
            for (int index = 0; index < values.length; index++) {
                statement.setObject(index + 1, values[index]);
            }
            return statement;
        }, keyHolder);
        Number generatedKey = keyHolder.getKey();
        if (generatedKey == null) {
            throw new IllegalStateException("No se genero el identificador");
        }
        return generatedKey.longValue();
    }
}
package uy.ccisj.api.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/auth")
public class AuthController {
    private final AuthService authService;
    private final ProfilePhotoService photos;
    private final AuthRateLimitService rateLimit;

    public AuthController(AuthService authService, ProfilePhotoService photos, AuthRateLimitService rateLimit) {
        this.authService = authService;
        this.photos = photos;
        this.rateLimit = rateLimit;
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest http) {
        rateLimit.assertLoginAllowed(http, request.identifier());
        try {
            AuthResponse response = authService.login(request);
            rateLimit.clearLogin(http, request.identifier());
            return response;
        } catch (ResponseStatusException exception) {
            if (exception.getStatusCode() == HttpStatus.UNAUTHORIZED) {
                rateLimit.recordLoginFailure(http, request.identifier());
            }
            throw exception;
        }
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(Authentication authentication) {
        authService.logout(authentication.getName());
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthResponse register(@Valid @RequestBody RegisterApplicantRequest request, HttpServletRequest http) {
        rateLimit.assertPublicAllowed(http, "register");
        return authService.registerApplicant(request);
    }

    @PostMapping("/change-password")
    public AuthResponse changePassword(Authentication authentication, @Valid @RequestBody ChangePasswordRequest request) {
        return authService.changePassword(authentication.getName(), request);
    }

    @PostMapping("/register/availability")
    public RegistrationAvailability availability(@Valid @RequestBody RegistrationAvailabilityRequest request,
            HttpServletRequest http) {
        rateLimit.assertPublicAllowed(http, "availability");
        return new RegistrationAvailability(false, false);
    }

    public record RegistrationAvailabilityRequest(
            @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Email
            @jakarta.validation.constraints.Size(max = 254) String email,
            @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max = 30) String identityCard) {}

    public record RegistrationAvailability(boolean emailExists, boolean identityCardExists) {}


    @GetMapping("/me")
    public Map<String, Object> currentUser(Authentication authentication) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("email", authentication.getName());
        response.put("roles", authentication.getAuthorities());
        response.put("fullName", authService.applicantFullName(authentication.getName()));
        response.put("esDirectivo", authService.socioEsDirectivo(authentication.getName()));
        response.put("debeCambiarPassword", authService.debeCambiarPassword(authentication.getName()));
        response.put("fotoUrl", photos.find(authentication.getName()));
        return response;
    }
}

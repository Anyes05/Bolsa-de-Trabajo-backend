package uy.ccisj.api.auth;

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

@RestController
@RequestMapping("/auth")
public class AuthController {
    private final AuthService authService;
    private final ProfilePhotoService photos;

    public AuthController(AuthService authService, ProfilePhotoService photos) {
        this.authService = authService;
        this.photos = photos;
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthResponse register(@Valid @RequestBody RegisterApplicantRequest request) {
        return authService.registerApplicant(request);
    }

    @PostMapping("/change-password")
    public AuthResponse changePassword(Authentication authentication, @Valid @RequestBody ChangePasswordRequest request) {
        return authService.changePassword(authentication.getName(), request);
    }

    @PostMapping("/register/availability")
    public RegistrationAvailability availability(@Valid @RequestBody RegistrationAvailabilityRequest request) {
        return authService.registrationAvailability(request.email(), request.identityCard());
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
        response.put("fotoUrl", photos.find(authentication.getName()));
        response.put("esDirectivo", authService.socioEsDirectivo(authentication.getName()));
        response.put("debeCambiarPassword", authService.debeCambiarPassword(authentication.getName()));
        return response;
    }
}

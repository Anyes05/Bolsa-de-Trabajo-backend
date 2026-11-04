package uy.ccisj.api.auth;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth/google")
public class GoogleAuthController {
    private final GoogleAuthService googleAuth;

    public GoogleAuthController(GoogleAuthService googleAuth) {
        this.googleAuth = googleAuth;
    }

    @PostMapping
    public GoogleAuthService.GoogleAuthResult authenticate(@Valid @RequestBody GoogleCredential request) {
        return googleAuth.authenticate(request.credential(), request.nonce(), null);
    }

    @PostMapping("/link")
    public GoogleAuthService.GoogleAuthResult link(@Valid @RequestBody GoogleLinkRequest request) {
        return googleAuth.authenticate(request.credential(), request.nonce(), request.password());
    }

    @PostMapping("/register")
    public AuthResponse register(@Valid @RequestBody GoogleRegistrationRequest request) {
        return googleAuth.register(request);
    }

    public record GoogleCredential(@NotBlank @Size(max = 8192) String credential,
            @NotBlank @Size(max = 128) String nonce) {}
    public record GoogleLinkRequest(@NotBlank @Size(max = 8192) String credential,
            @NotBlank @Size(max = 128) String nonce, @NotBlank @Size(max = 128) String password) {}
}

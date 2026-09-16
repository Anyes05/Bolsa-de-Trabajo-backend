package uy.ccisj.api.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import uy.ccisj.api.user.Role;

public record LoginRequest(
        @NotNull Role role,
        @NotBlank String identifier,
        @NotBlank String password) {
}
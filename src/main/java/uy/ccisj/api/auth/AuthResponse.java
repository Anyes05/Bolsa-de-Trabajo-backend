package uy.ccisj.api.auth;

import uy.ccisj.api.user.Role;

public record AuthResponse(String token, String email, Role role, String fullName) {
}
package uy.ccisj.api.auth;

import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import uy.ccisj.api.user.UserRepository;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private static final Logger LOGGER = LoggerFactory.getLogger(JwtAuthenticationFilter.class);
    private final JwtService jwtService;
    private final UserRepository userRepository;

    public JwtAuthenticationFilter(JwtService jwtService, UserRepository userRepository) {
        this.jwtService = jwtService;
        this.userRepository = userRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        /// Traza enfocada en 403: permite distinguir token ausente, invalido o usuario inactivo.
        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7);
            if (!jwtService.isValid(token)) {
                LOGGER.warn("JWT invalido para {} {}", request.getMethod(), request.getRequestURI());
            } else {
                String email = jwtService.extractEmail(token);
                userRepository.findByEmailIgnoreCase(email)
                        .ifPresentOrElse(user -> {
                            if (!user.isActivo()) {
                                LOGGER.warn("JWT valido pero usuario inactivo email={} uri={}", email, request.getRequestURI());
                                return;
                            }
                            var authority = new SimpleGrantedAuthority("ROLE_" + user.getRole().name());
                            var authentication = new UsernamePasswordAuthenticationToken(user.getEmail(), null, java.util.List.of(authority));
                            SecurityContextHolder.getContext().setAuthentication(authentication);
                        }, () -> LOGGER.warn("JWT valido pero usuario no encontrado email={} uri={}", email, request.getRequestURI()));
            }
        }
        filterChain.doFilter(request, response);
    }
}
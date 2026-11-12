package uy.ccisj.api.auth;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import jakarta.servlet.http.HttpServletRequest;

@Service
public class AuthRateLimitService {
    private static final int LOGIN_MAX = 5;
    private static final Duration LOGIN_WINDOW = Duration.ofMinutes(15);
    private static final int PUBLIC_MAX = 10;
    private static final Duration PUBLIC_WINDOW = Duration.ofMinutes(10);

    private final ConcurrentHashMap<String, List<Instant>> hits = new ConcurrentHashMap<>();

    public void assertLoginAllowed(HttpServletRequest request, String identifier) {
        assertUnder(key("login", clientIp(request), identifier), LOGIN_MAX, LOGIN_WINDOW,
                "Demasiados intentos de ingreso. Esperá 15 minutos e intentá de nuevo.");
    }

    public void recordLoginFailure(HttpServletRequest request, String identifier) {
        record(key("login", clientIp(request), identifier));
    }

    public void clearLogin(HttpServletRequest request, String identifier) {
        hits.remove(key("login", clientIp(request), identifier));
    }

    public void assertPublicAllowed(HttpServletRequest request, String action) {
        assertUnder(key(action, clientIp(request), ""), PUBLIC_MAX, PUBLIC_WINDOW,
                "Demasiadas solicitudes. Esperá unos minutos e intentá de nuevo.");
        record(key(action, clientIp(request), ""));
    }

    public static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr() == null ? "unknown" : request.getRemoteAddr();
    }

    private void assertUnder(String key, int max, Duration window, String message) {
        List<Instant> recent = prune(key, window);
        if (recent.size() >= max) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, message);
        }
    }

    private void record(String key) {
        hits.compute(key, (ignored, current) -> {
            List<Instant> next = current == null ? new ArrayList<>() : new ArrayList<>(current);
            next.add(Instant.now());
            return next;
        });
    }

    private List<Instant> prune(String key, Duration window) {
        Instant cutoff = Instant.now().minus(window);
        return hits.compute(key, (ignored, current) -> {
            List<Instant> next = current == null ? new ArrayList<>() : new ArrayList<>(current);
            Iterator<Instant> iterator = next.iterator();
            while (iterator.hasNext()) {
                if (iterator.next().isBefore(cutoff)) {
                    iterator.remove();
                }
            }
            return next;
        });
    }

    private static String key(String action, String ip, String identifier) {
        String id = identifier == null ? "" : identifier.trim().toLowerCase();
        return action + "|" + ip + "|" + id;
    }
}

package uy.ccisj.api.auth;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import java.io.IOException;
import java.net.URI;
import java.security.GeneralSecurityException;
import java.util.List;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class GoogleTokenVerifier {
    private final GoogleIdTokenVerifier verifier;
    private boolean configured = true;

    @Autowired
    public GoogleTokenVerifier(@Value("${app.google.client-id}") String clientId) {
        this(new GoogleIdTokenVerifier.Builder(new NetHttpTransport(), GsonFactory.getDefaultInstance())
                .setAudience(List.of(clientId)).setAcceptableTimeSkewSeconds(0).build());
        configured = !clientId.isBlank();
    }

    GoogleTokenVerifier(GoogleIdTokenVerifier verifier) {
        this.verifier = verifier;
    }

    public GoogleIdentity verify(String credential, String nonce) {
        if (!configured) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "El acceso con Google no esta configurado. Contacta al administrador.");
        }
        GoogleIdToken token;
        try {
            token = GoogleIdToken.parse(GsonFactory.getDefaultInstance(), credential);
        } catch (IOException | IllegalArgumentException exception) {
            throw invalidToken();
        }
        try {
            if (!verifier.verify(token)) throw invalidToken();
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "No pudimos verificar tu cuenta con Google. Intenta nuevamente.", exception);
        } catch (GeneralSecurityException exception) {
            throw invalidToken();
        }
        var payload = token.getPayload();
        if (!Boolean.TRUE.equals(payload.getEmailVerified())
                || payload.getSubject() == null || payload.getSubject().isBlank()
                || payload.getEmail() == null || payload.getEmail().isBlank()
                || nonce == null || nonce.isBlank() || !nonce.equals(payload.get("nonce"))) throw invalidToken();
        String email = payload.getEmail().trim().toLowerCase(Locale.ROOT);
        String name = payload.get("name") instanceof String value && !value.isBlank() ? value.trim() : email;
        String picture = payload.get("picture") instanceof String value ? value : null;
        if (picture != null) {
            URI uri;
            try {
                uri = URI.create(picture);
            } catch (IllegalArgumentException exception) {
                throw invalidToken();
            }
            if (!"https".equals(uri.getScheme()) || uri.getHost() == null
                    || !uri.getHost().endsWith(".googleusercontent.com")) picture = null;
        }
        boolean authoritative = email.endsWith("@gmail.com")
                || (payload.get("hd") instanceof String domain && !domain.isBlank());
        return new GoogleIdentity(payload.getSubject(), email, name, picture, authoritative);
    }

    private ResponseStatusException invalidToken() {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                "La autenticacion de Google no es valida o vencio. Inicia sesion con Google nuevamente.");
    }

    public record GoogleIdentity(String subject, String email, String fullName, String fotoUrl, boolean authoritative) {}
}

package br.edu.lab2.carros.security;

import br.edu.lab2.carros.domain.UserAccount;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class SessionRegistry {
    private final Map<String, Session> sessions = new ConcurrentHashMap<>();
    private final SecureRandom secureRandom = new SecureRandom();
    private final long sessionHours;

    public SessionRegistry(@Value("${app.auth.session-hours:12}") long sessionHours) {
        this.sessionHours = Math.max(1, sessionHours);
    }

    public String create(UserAccount account) {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        sessions.put(token, new Session(new AuthPrincipal(account.id, account.username, account.role), Instant.now().plusSeconds(sessionHours * 3600)));
        return token;
    }

    public AuthPrincipal resolve(String token) {
        Session session = sessions.get(token);
        if (session == null) return null;
        if (session.expiresAt().isBefore(Instant.now())) {
            sessions.remove(token);
            return null;
        }
        return session.principal();
    }

    public void revoke(String token) {
        if (token != null) sessions.remove(token);
    }

    public void revokeUser(UUID userId) {
        sessions.entrySet().removeIf(entry -> entry.getValue().principal().userId().equals(userId));
    }

    private record Session(AuthPrincipal principal, Instant expiresAt) {}
}

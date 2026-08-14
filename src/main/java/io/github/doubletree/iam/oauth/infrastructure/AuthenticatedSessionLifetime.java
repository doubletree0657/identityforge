package io.github.doubletree.iam.oauth.infrastructure;

import jakarta.servlet.http.HttpSession;
import java.time.Instant;

/** Tracks the absolute browser-session lifetime from completed authentication. */
public final class AuthenticatedSessionLifetime {

    static final String AUTHENTICATED_AT_ATTRIBUTE =
            AuthenticatedSessionLifetime.class.getName() + ".AUTHENTICATED_AT";

    private AuthenticatedSessionLifetime() {
    }

    public static void markAuthenticated(HttpSession session) {
        if (session != null) {
            session.setAttribute(AUTHENTICATED_AT_ATTRIBUTE, Instant.now());
        }
    }

    static Instant initializeLegacySession(HttpSession session) {
        Instant authenticatedAt = Instant.ofEpochMilli(session.getCreationTime());
        session.setAttribute(AUTHENTICATED_AT_ATTRIBUTE, authenticatedAt);
        return authenticatedAt;
    }

    static Instant authenticatedAt(HttpSession session) {
        if (session == null) {
            return null;
        }
        Object value = session.getAttribute(AUTHENTICATED_AT_ATTRIBUTE);
        return value instanceof Instant instant ? instant : null;
    }
}

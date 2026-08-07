package io.github.doubletree.iam.authentication.infrastructure;

import io.github.doubletree.iam.shared.security.ActorContext;
import io.github.doubletree.iam.shared.security.ActorType;
import io.github.doubletree.iam.shared.security.CurrentActor;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

@Component
public class SecurityContextCurrentActor implements CurrentActor {

    @Override
    public ActorContext get() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken token) || !authentication.isAuthenticated()) {
            return ActorContext.system();
        }

        Jwt jwt = token.getToken();
        return new ActorContext(
                actorType(jwt),
                uuidClaim(jwt, "user_id"),
                uuidClaim(jwt, "tenant_id"),
                Boolean.TRUE.equals(jwt.getClaim("platform_operator")),
                claimSet(jwt, "effective_roles", "roles"),
                claimSet(jwt, "effective_permissions", "permissions"),
                claimSet(jwt, "scope", null),
                intClaim(jwt, "security_version"));
    }

    private ActorType actorType(Jwt jwt) {
        return jwt.hasClaim("user_id") ? ActorType.USER : ActorType.CLIENT;
    }

    private UUID uuidClaim(Jwt jwt, String name) {
        String value = jwt.getClaimAsString(name);
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException exception) {
            throw new AccessDeniedException("Token contains an invalid " + name + " claim");
        }
    }

    private int intClaim(Jwt jwt, String name) {
        Number value = jwt.getClaim(name);
        return value == null ? 0 : value.intValue();
    }

    private Set<String> claimSet(Jwt jwt, String primary, String fallback) {
        Set<String> values = values(jwt, primary);
        return values.isEmpty() && fallback != null ? values(jwt, fallback) : values;
    }

    private Set<String> values(Jwt jwt, String name) {
        Object claim = jwt.getClaims().get(name);
        if (claim instanceof String value) {
            return Arrays.stream(value.split(" "))
                    .filter(item -> !item.isBlank())
                    .collect(Collectors.toCollection(LinkedHashSet::new));
        }
        var values = jwt.getClaimAsStringList(name);
        return values == null ? Set.of() : new LinkedHashSet<>(values);
    }
}

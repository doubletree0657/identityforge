package io.github.doubletree.iam.platform.security;

import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;

public class AdminApiAuthorizationManager implements AuthorizationManager<RequestAuthorizationContext> {

    private final String requiredScope;

    public AdminApiAuthorizationManager(String requiredScope) {
        this.requiredScope = requiredScope;
    }

    @Override
    public AuthorizationDecision check(
            Supplier<Authentication> authenticationSupplier,
            RequestAuthorizationContext context) {
        Authentication authentication = authenticationSupplier.get();
        if (!(authentication instanceof JwtAuthenticationToken token) || !authentication.isAuthenticated()) {
            return new AuthorizationDecision(false);
        }

        Jwt jwt = token.getToken();
        Set<String> scopes = claimSet(jwt, "scope");
        if (!scopes.contains(requiredScope)) {
            return new AuthorizationDecision(false);
        }

        Set<String> roles = claimSet(jwt, "effective_roles");
        if (roles.isEmpty()) {
            roles = claimSet(jwt, "roles");
        }
        Set<String> permissions = claimSet(jwt, "effective_permissions");
        if (permissions.isEmpty()) {
            permissions = claimSet(jwt, "permissions");
        }
        return new AuthorizationDecision(AdminAuthorities.hasAdminAccess(roles, permissions));
    }

    private Set<String> claimSet(Jwt jwt, String claimName) {
        Object claim = jwt.getClaims().get(claimName);
        if (claim instanceof String value) {
            return Arrays.stream(value.split(" "))
                    .filter(item -> !item.isBlank())
                    .collect(Collectors.toCollection(LinkedHashSet::new));
        }
        Collection<String> values = jwt.getClaimAsStringList(claimName);
        return values == null ? Set.of() : new LinkedHashSet<>(values);
    }
}

package io.github.doubletree.iam.oauth.infrastructure;

import java.util.Collection;
import java.util.Set;
import java.util.function.Supplier;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;

public class AudienceScopeAuthorizationManager implements AuthorizationManager<RequestAuthorizationContext> {

    private final String audience;
    private final String scope;

    public AudienceScopeAuthorizationManager(String audience, String scope) {
        this.audience = audience;
        this.scope = scope;
    }

    @Override
    public AuthorizationDecision check(
            Supplier<Authentication> authenticationSupplier,
            RequestAuthorizationContext context) {
        Authentication authentication = authenticationSupplier.get();
        if (!(authentication instanceof JwtAuthenticationToken token) || !authentication.isAuthenticated()) {
            return new AuthorizationDecision(false);
        }
        Collection<String> audiences = token.getToken().getAudience();
        Set<String> scopes = token.getToken().getClaimAsStringList("scope") == null
                ? Set.of()
                : Set.copyOf(token.getToken().getClaimAsStringList("scope"));
        return new AuthorizationDecision(
                audiences != null && audiences.contains(audience) && scopes.contains(scope));
    }
}

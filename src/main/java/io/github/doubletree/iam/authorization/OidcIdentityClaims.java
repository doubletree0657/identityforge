package io.github.doubletree.iam.authorization;

import io.github.doubletree.iam.security.authentication.PlatformUserDetails;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.springframework.security.oauth2.core.oidc.OidcScopes;
import org.springframework.util.StringUtils;

public class OidcIdentityClaims {

    public static final String GROUPS_SCOPE = "groups";
    public static final String ROLES_SCOPE = "roles";

    public Map<String, Object> idTokenClaims(PlatformUserDetails userDetails, Set<String> scopes) {
        Map<String, Object> claims = basicIdentityClaims(userDetails);
        if (scopes.contains(OidcScopes.PROFILE)) {
            claims.put("account_status", userDetails.accountStatus().name());
        }
        addEmailClaims(claims, userDetails, scopes);
        return claims;
    }

    public Map<String, Object> userInfoClaims(PlatformUserDetails userDetails, Set<String> scopes) {
        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("sub", userDetails.userId().toString());

        if (scopes.contains(OidcScopes.PROFILE)) {
            claims.putAll(basicIdentityClaims(userDetails));
            claims.put("account_status", userDetails.accountStatus().name());
        }
        addEmailClaims(claims, userDetails, scopes);
        if (scopes.contains(GROUPS_SCOPE)) {
            claims.put("groups", userDetails.groups());
        }
        if (scopes.contains(ROLES_SCOPE)) {
            claims.put("roles", userDetails.directRoles());
            claims.put("effective_roles", userDetails.effectiveRoles());
        }
        return claims;
    }

    private Map<String, Object> basicIdentityClaims(PlatformUserDetails userDetails) {
        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("sub", userDetails.userId().toString());
        claims.put("preferred_username", userDetails.username());
        claims.put("name", userDetails.displayName());
        claims.put("display_name", userDetails.displayName());
        claims.put("tenant_id", userDetails.tenantId().toString());
        claims.put("tenant_name", userDetails.tenantName());
        return claims;
    }

    private void addEmailClaims(
            Map<String, Object> claims,
            PlatformUserDetails userDetails,
            Set<String> scopes) {
        if (scopes.contains(OidcScopes.EMAIL) && StringUtils.hasText(userDetails.email())) {
            claims.put("email", userDetails.email());
            claims.put("email_verified", userDetails.emailVerified());
        }
    }
}

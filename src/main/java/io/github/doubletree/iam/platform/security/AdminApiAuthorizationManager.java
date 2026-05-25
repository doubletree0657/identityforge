package io.github.doubletree.iam.platform.security;

import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Locale;
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
        return new AuthorizationDecision(hasAccess(context, roles, permissions));
    }

    private boolean hasAccess(RequestAuthorizationContext context, Set<String> roles, Set<String> permissions) {
        String requiredPermission = requiredPermission(
                context.getRequest().getMethod(),
                context.getRequest().getRequestURI());
        if (requiredPermission == null) {
            return false;
        }
        if (AdminAuthorities.isPlatformAdmin(roles)) {
            return true;
        }
        if (permissions.contains(BuiltInPermission.IAM_ADMIN.permissionName())) {
            return true;
        }
        return AdminAuthorities.hasAdminAccess(roles, permissions)
                && permissions.contains(requiredPermission);
    }

    private String requiredPermission(String method, String path) {
        boolean write = !"GET".equalsIgnoreCase(method) && !"HEAD".equalsIgnoreCase(method);
        if (path.startsWith("/api/tenants")) {
            return write ? BuiltInPermission.TENANTS_WRITE.permissionName() : BuiltInPermission.TENANTS_READ.permissionName();
        }
        if (path.startsWith("/api/mfa")) {
            return BuiltInPermission.MFA_MANAGE.permissionName();
        }
        if (path.startsWith("/api/users") && path.contains("/mfa/totp")) {
            return BuiltInPermission.MFA_MANAGE.permissionName();
        }
        if (path.startsWith("/api/users")) {
            return write ? BuiltInPermission.USERS_WRITE.permissionName() : BuiltInPermission.USERS_READ.permissionName();
        }
        if (path.startsWith("/api/groups")) {
            return write ? BuiltInPermission.GROUPS_WRITE.permissionName() : BuiltInPermission.GROUPS_READ.permissionName();
        }
        if (path.startsWith("/api/roles")) {
            return write ? BuiltInPermission.ROLES_WRITE.permissionName() : BuiltInPermission.ROLES_READ.permissionName();
        }
        if (path.startsWith("/api/permissions")) {
            return write ? BuiltInPermission.PERMISSIONS_WRITE.permissionName() : BuiltInPermission.PERMISSIONS_READ.permissionName();
        }
        if (path.startsWith("/api/clients")) {
            return write ? BuiltInPermission.CLIENTS_WRITE.permissionName() : BuiltInPermission.CLIENTS_READ.permissionName();
        }
        if (path.startsWith("/api/audit-logs")) {
            return write ? null : BuiltInPermission.AUDIT_READ.permissionName();
        }
        return write ? BuiltInPermission.IAM_ADMIN.permissionName() : BuiltInPermission.IAM_ADMIN.permissionName();
    }

    private Set<String> claimSet(Jwt jwt, String claimName) {
        Object claim = jwt.getClaims().get(claimName);
        if (claim instanceof String value) {
            return Arrays.stream(value.split(" "))
                    .map(item -> item.toLowerCase(Locale.ROOT))
                    .filter(item -> !item.isBlank())
                    .collect(Collectors.toCollection(LinkedHashSet::new));
        }
        Collection<String> values = jwt.getClaimAsStringList(claimName);
        return values == null
                ? Set.of()
                : values.stream()
                        .map(item -> item.toLowerCase(Locale.ROOT))
                        .collect(Collectors.toCollection(LinkedHashSet::new));
    }
}

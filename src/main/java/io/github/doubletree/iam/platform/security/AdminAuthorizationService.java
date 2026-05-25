package io.github.doubletree.iam.platform.security;

import io.github.doubletree.iam.platform.application.exception.TenantBoundaryViolationException;
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
import org.springframework.stereotype.Service;

@Service
public class AdminAuthorizationService {

    public UUID tenantIdForList(UUID requestedTenantId) {
        if (!hasCurrentJwt()) {
            return requestedTenantId;
        }
        if (isPlatformAdmin()) {
            return requestedTenantId;
        }
        UUID currentTenantId = currentTenantId();
        if (requestedTenantId != null && !requestedTenantId.equals(currentTenantId)) {
            throw new AccessDeniedException("Tenant administrators can only access their own tenant");
        }
        return currentTenantId;
    }

    public void assertTenantAccess(UUID tenantId) {
        if (!hasCurrentJwt()) {
            return;
        }
        if (tenantId == null || isPlatformAdmin()) {
            return;
        }
        if (!tenantId.equals(currentTenantId())) {
            throw new AccessDeniedException("Tenant administrators can only access their own tenant");
        }
    }

    public void assertSameTenant(UUID firstTenantId, UUID secondTenantId, String message) {
        if (!firstTenantId.equals(secondTenantId)) {
            throw new TenantBoundaryViolationException(message);
        }
        assertTenantAccess(firstTenantId);
    }

    public boolean isPlatformAdmin() {
        if (!hasCurrentJwt()) {
            return true;
        }
        return AdminAuthorities.isPlatformAdmin(currentRoles());
    }

    private UUID currentTenantId() {
        String tenantId = currentJwt().getClaimAsString("tenant_id");
        if (tenantId == null || tenantId.isBlank()) {
            throw new AccessDeniedException("Admin token is missing tenant_id");
        }
        return UUID.fromString(tenantId);
    }

    private Set<String> currentRoles() {
        Jwt jwt = currentJwt();
        Set<String> roles = claimSet(jwt, "effective_roles");
        if (roles.isEmpty()) {
            roles = claimSet(jwt, "roles");
        }
        return roles;
    }

    private Jwt currentJwt() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken token) {
            return token.getToken();
        }
        throw new AccessDeniedException("Admin API requires a JWT access token");
    }

    private boolean hasCurrentJwt() {
        return SecurityContextHolder.getContext().getAuthentication() instanceof JwtAuthenticationToken;
    }

    private Set<String> claimSet(Jwt jwt, String claimName) {
        Object claim = jwt.getClaims().get(claimName);
        if (claim instanceof String value) {
            return Arrays.stream(value.split(" "))
                    .filter(item -> !item.isBlank())
                    .collect(Collectors.toCollection(LinkedHashSet::new));
        }
        return new LinkedHashSet<>(jwt.getClaimAsStringList(claimName) == null
                ? Set.of()
                : jwt.getClaimAsStringList(claimName));
    }
}

package io.github.doubletree.iam.web.dto;

import java.util.Set;

public record CurrentUserResponse(
        String subject,
        String username,
        String userId,
        String tenantId,
        String displayName,
        Set<String> roles,
        Set<String> scopes,
        Set<String> directRoles,
        Set<String> groupRoles,
        Set<String> effectiveRoles,
        Set<String> effectivePermissions,
        boolean isPlatformAdmin,
        boolean isTenantAdmin) {
}

package io.github.doubletree.iam.web.dto;

import io.github.doubletree.iam.domain.Role;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public record RoleResponse(
        UUID id,
        UUID tenantId,
        String name,
        Instant createdAt,
        Instant updatedAt,
        Set<UUID> permissionIds,
        long userAssignmentCount,
        long groupAssignmentCount) {

    public static RoleResponse from(Role role) {
        Set<UUID> permissionIds = role.getPermissions().stream()
                .map(permission -> permission.getId())
                .collect(Collectors.toSet());
        return new RoleResponse(
                role.getId(),
                role.getTenant().getId(),
                role.getName(),
                role.getCreatedAt(),
                role.getUpdatedAt(),
                permissionIds,
                0,
                0);
    }
}

package io.github.doubletree.iam.platform.web.dto;

import io.github.doubletree.iam.platform.domain.Permission;
import java.time.Instant;
import java.util.UUID;

public record PermissionResponse(UUID id, UUID tenantId, String name, Instant createdAt, Instant updatedAt) {

    public static PermissionResponse from(Permission permission) {
        return new PermissionResponse(
                permission.getId(),
                permission.getTenant().getId(),
                permission.getName(),
                permission.getCreatedAt(),
                permission.getUpdatedAt());
    }
}

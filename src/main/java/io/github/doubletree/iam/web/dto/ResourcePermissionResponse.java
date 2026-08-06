package io.github.doubletree.iam.web.dto;

import io.github.doubletree.iam.domain.ResourcePermission;
import java.time.Instant;
import java.util.UUID;

public record ResourcePermissionResponse(
        UUID id,
        UUID resourceServerId,
        String name,
        String displayName,
        String description,
        Instant createdAt,
        Instant updatedAt) {

    public static ResourcePermissionResponse from(ResourcePermission permission) {
        return new ResourcePermissionResponse(
                permission.getId(),
                permission.getResourceServer().getId(),
                permission.getName(),
                permission.getDisplayName(),
                permission.getDescription(),
                permission.getCreatedAt(),
                permission.getUpdatedAt());
    }
}

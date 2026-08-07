package io.github.doubletree.iam.directory.web.dto;

import io.github.doubletree.iam.directory.domain.Permission;
import java.time.Instant;
import java.util.UUID;

public record PermissionResponse(
        UUID id,
        String name,
        String displayName,
        String description,
        String category,
        boolean systemManaged,
        Instant createdAt,
        Instant updatedAt) {

    public static PermissionResponse from(Permission permission) {
        return new PermissionResponse(
                permission.getId(),
                permission.getName(),
                permission.getDisplayName(),
                permission.getDescription(),
                permission.getCategory(),
                permission.isSystemManaged(),
                permission.getCreatedAt(),
                permission.getUpdatedAt());
    }
}

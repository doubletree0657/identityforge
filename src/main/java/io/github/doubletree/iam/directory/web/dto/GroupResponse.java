package io.github.doubletree.iam.directory.web.dto;

import io.github.doubletree.iam.directory.domain.Group;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public record GroupResponse(
        UUID id,
        UUID tenantId,
        String name,
        String displayName,
        String description,
        Instant createdAt,
        Instant updatedAt,
        Set<UUID> memberIds,
        Set<UUID> roleIds) {

    public static GroupResponse from(Group group) {
        Set<UUID> memberIds = group.getUsers().stream()
                .map(user -> user.getId())
                .collect(Collectors.toSet());
        Set<UUID> roleIds = group.getRoles().stream()
                .map(role -> role.getId())
                .collect(Collectors.toSet());
        return new GroupResponse(
                group.getId(),
                group.getTenant().getId(),
                group.getName(),
                group.getDisplayName(),
                group.getDescription(),
                group.getCreatedAt(),
                group.getUpdatedAt(),
                memberIds,
                roleIds);
    }
}

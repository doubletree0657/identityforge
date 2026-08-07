package io.github.doubletree.iam.applications.web.dto;

import io.github.doubletree.iam.applications.domain.ResourceServer;
import io.github.doubletree.iam.applications.domain.ResourceServerStatus;
import java.time.Instant;
import java.util.UUID;

public record ResourceServerResponse(
        UUID id,
        UUID tenantId,
        String identifier,
        String name,
        String description,
        ResourceServerStatus status,
        Instant createdAt,
        Instant updatedAt) {

    public static ResourceServerResponse from(ResourceServer resourceServer) {
        return new ResourceServerResponse(
                resourceServer.getId(),
                resourceServer.getTenant().getId(),
                resourceServer.getIdentifier(),
                resourceServer.getName(),
                resourceServer.getDescription(),
                resourceServer.getStatus(),
                resourceServer.getCreatedAt(),
                resourceServer.getUpdatedAt());
    }
}

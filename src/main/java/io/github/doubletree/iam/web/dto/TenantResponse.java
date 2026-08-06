package io.github.doubletree.iam.web.dto;

import io.github.doubletree.iam.domain.Tenant;
import io.github.doubletree.iam.domain.TenantStatus;
import java.time.Instant;
import java.util.UUID;

public record TenantResponse(UUID id, String name, String slug, TenantStatus status, Instant createdAt, Instant updatedAt) {

    public static TenantResponse from(Tenant tenant) {
        return new TenantResponse(
                tenant.getId(),
                tenant.getName(),
                tenant.getSlug(),
                tenant.getStatus(),
                tenant.getCreatedAt(),
                tenant.getUpdatedAt());
    }
}

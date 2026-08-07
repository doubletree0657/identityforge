package io.github.doubletree.iam.directory.web.dto;

import io.github.doubletree.iam.directory.domain.TenantStatus;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateTenantRequest(
        @Size(min = 1, max = 120) String name,
        @Size(min = 1, max = 80) @Pattern(regexp = "[a-z0-9][a-z0-9-]*[a-z0-9]|[a-z0-9]") String slug,
        TenantStatus status) {
}

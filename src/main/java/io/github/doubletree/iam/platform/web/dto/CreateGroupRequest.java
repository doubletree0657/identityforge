package io.github.doubletree.iam.platform.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record CreateGroupRequest(
        @NotNull UUID tenantId,
        @NotBlank String name,
        String displayName,
        String description) {
}

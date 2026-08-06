package io.github.doubletree.iam.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

public record ScimCreateGroupRequest(
        @NotNull UUID tenantId,
        @NotBlank String displayName,
        List<UUID> members) {
}

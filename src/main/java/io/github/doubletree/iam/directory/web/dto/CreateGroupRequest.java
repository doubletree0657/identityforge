package io.github.doubletree.iam.directory.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record CreateGroupRequest(
        @NotNull UUID tenantId,
        @NotBlank @Size(max = 160) String name,
        @Size(max = 160) String displayName,
        @Size(max = 500) String description) {
}

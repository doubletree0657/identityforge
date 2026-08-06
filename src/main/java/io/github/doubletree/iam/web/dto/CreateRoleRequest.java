package io.github.doubletree.iam.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record CreateRoleRequest(
        @NotNull UUID tenantId,
        @NotBlank @Size(max = 160) String name) {
}

package io.github.doubletree.iam.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record ScimCreateUserRequest(
        @NotNull UUID tenantId,
        @NotBlank String userName,
        @NotBlank String displayName) {
}

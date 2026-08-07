package io.github.doubletree.iam.applications.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record CreateResourceServerRequest(
        @NotNull UUID tenantId,
        @NotBlank @Size(max = 255) String identifier,
        @NotBlank @Size(max = 255) String name,
        @Size(max = 2000) String description) {
}

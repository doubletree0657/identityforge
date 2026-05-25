package io.github.doubletree.iam.platform.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateResourcePermissionRequest(
        @NotBlank @Size(max = 255) String name,
        @Size(max = 255) String displayName,
        @Size(max = 2000) String description) {
}

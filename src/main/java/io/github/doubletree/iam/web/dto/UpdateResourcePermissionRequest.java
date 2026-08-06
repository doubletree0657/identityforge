package io.github.doubletree.iam.web.dto;

import jakarta.validation.constraints.Size;

public record UpdateResourcePermissionRequest(
        @Size(max = 255) String name,
        @Size(max = 255) String displayName,
        @Size(max = 2000) String description) {
}

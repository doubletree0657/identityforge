package io.github.doubletree.iam.platform.web.dto;

import jakarta.validation.constraints.Size;

public record UpdateGroupRequest(
        @Size(min = 1, max = 160) String name,
        @Size(min = 1, max = 160) String displayName,
        @Size(max = 500) String description) {
}

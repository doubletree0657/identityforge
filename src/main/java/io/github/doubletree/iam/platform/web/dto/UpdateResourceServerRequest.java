package io.github.doubletree.iam.platform.web.dto;

import io.github.doubletree.iam.platform.domain.ResourceServerStatus;
import jakarta.validation.constraints.Size;

public record UpdateResourceServerRequest(
        @Size(max = 255) String identifier,
        @Size(max = 255) String name,
        @Size(max = 2000) String description,
        ResourceServerStatus status) {
}

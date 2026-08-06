package io.github.doubletree.iam.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateTenantRequest(@NotBlank @Size(max = 120) String name) {
}

package io.github.doubletree.iam.provisioning.web.dto;

import jakarta.validation.constraints.NotBlank;

public record ScimCreateUserRequest(
        @NotBlank String userName,
        @NotBlank String displayName) {
}

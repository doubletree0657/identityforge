package io.github.doubletree.iam.directory.web.dto;

import jakarta.validation.constraints.NotBlank;

public record UpdateUserPasswordRequest(
        @NotBlank String newPassword,
        Boolean passwordResetRequired) {
}

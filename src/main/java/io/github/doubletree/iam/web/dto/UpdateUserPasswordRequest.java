package io.github.doubletree.iam.web.dto;

import jakarta.validation.constraints.NotBlank;

public record UpdateUserPasswordRequest(
        @NotBlank String newPassword,
        Boolean passwordResetRequired) {
}

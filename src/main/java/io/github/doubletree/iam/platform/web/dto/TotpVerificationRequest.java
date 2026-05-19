package io.github.doubletree.iam.platform.web.dto;

import jakarta.validation.constraints.NotBlank;

public record TotpVerificationRequest(@NotBlank String code) {
}

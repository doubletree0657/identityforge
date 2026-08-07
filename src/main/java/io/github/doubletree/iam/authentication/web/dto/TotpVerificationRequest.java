package io.github.doubletree.iam.authentication.web.dto;

import jakarta.validation.constraints.NotBlank;

public record TotpVerificationRequest(@NotBlank String code) {
}

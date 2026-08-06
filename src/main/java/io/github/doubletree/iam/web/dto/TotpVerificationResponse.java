package io.github.doubletree.iam.web.dto;

import java.util.UUID;

public record TotpVerificationResponse(UUID userId, boolean verified) {
}

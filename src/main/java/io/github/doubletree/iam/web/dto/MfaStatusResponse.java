package io.github.doubletree.iam.web.dto;

import java.util.UUID;

public record MfaStatusResponse(UUID userId, boolean totpEnabled) {
}

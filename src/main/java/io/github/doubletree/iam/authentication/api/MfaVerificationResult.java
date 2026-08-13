package io.github.doubletree.iam.authentication.api;

import java.util.List;
import java.util.UUID;

public record MfaVerificationResult(UUID userId, boolean verified, List<String> recoveryCodes) {

    public MfaVerificationResult {
        recoveryCodes = List.copyOf(recoveryCodes);
    }
}

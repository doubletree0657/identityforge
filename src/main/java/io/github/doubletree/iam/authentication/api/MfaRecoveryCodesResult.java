package io.github.doubletree.iam.authentication.api;

import java.util.List;
import java.util.UUID;

public record MfaRecoveryCodesResult(UUID userId, List<String> recoveryCodes) {

    public MfaRecoveryCodesResult {
        recoveryCodes = List.copyOf(recoveryCodes);
    }
}

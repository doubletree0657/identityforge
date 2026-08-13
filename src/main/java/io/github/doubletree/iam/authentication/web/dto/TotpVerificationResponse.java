package io.github.doubletree.iam.authentication.web.dto;

import io.github.doubletree.iam.authentication.api.MfaVerificationResult;
import java.util.List;
import java.util.UUID;

public record TotpVerificationResponse(UUID userId, boolean verified, List<String> recoveryCodes) {

    public static TotpVerificationResponse from(MfaVerificationResult result) {
        return new TotpVerificationResponse(result.userId(), result.verified(), result.recoveryCodes());
    }
}

package io.github.doubletree.iam.authentication.web.dto;

import io.github.doubletree.iam.authentication.api.MfaRecoveryCodesResult;
import java.util.List;
import java.util.UUID;

public record MfaRecoveryCodesResponse(UUID userId, List<String> recoveryCodes) {

    public static MfaRecoveryCodesResponse from(MfaRecoveryCodesResult result) {
        return new MfaRecoveryCodesResponse(result.userId(), result.recoveryCodes());
    }
}

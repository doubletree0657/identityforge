package io.github.doubletree.iam.authentication.web.dto;

import io.github.doubletree.iam.authentication.api.MfaStatus;
import java.util.UUID;

public record MfaStatusResponse(
        UUID userId,
        boolean totpEnrolled,
        boolean totpVerified,
        boolean enrollmentPending,
        long recoveryCodesRemaining,
        long recoveryCodesTotal) {

    public static MfaStatusResponse from(MfaStatus status) {
        return new MfaStatusResponse(
                status.userId(),
                status.totpEnrolled(),
                status.totpVerified(),
                status.enrollmentPending(),
                status.recoveryCodesRemaining(),
                status.recoveryCodesTotal());
    }
}

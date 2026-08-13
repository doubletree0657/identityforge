package io.github.doubletree.iam.authentication.api;

import java.util.UUID;

public record MfaStatus(
        UUID userId,
        boolean totpEnrolled,
        boolean totpVerified,
        boolean enrollmentPending,
        long recoveryCodesRemaining,
        long recoveryCodesTotal) {
}

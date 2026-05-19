package io.github.doubletree.iam.platform.web.dto;

import io.github.doubletree.iam.platform.application.result.MfaEnrollmentResult;
import java.util.UUID;

public record MfaEnrollmentResponse(UUID userId, String secret) {

    public static MfaEnrollmentResponse from(MfaEnrollmentResult result) {
        return new MfaEnrollmentResponse(result.userId(), result.secret());
    }
}

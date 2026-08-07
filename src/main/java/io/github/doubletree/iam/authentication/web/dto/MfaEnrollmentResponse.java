package io.github.doubletree.iam.authentication.web.dto;

import io.github.doubletree.iam.authentication.api.MfaEnrollmentResult;
import java.util.UUID;

public record MfaEnrollmentResponse(UUID userId, String secret, String otpauthUri) {

    public static MfaEnrollmentResponse from(MfaEnrollmentResult result) {
        return new MfaEnrollmentResponse(result.userId(), result.secret(), result.otpauthUri());
    }
}

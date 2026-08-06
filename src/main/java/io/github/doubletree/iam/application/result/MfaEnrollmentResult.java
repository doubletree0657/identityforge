package io.github.doubletree.iam.application.result;

import java.util.UUID;

public record MfaEnrollmentResult(UUID userId, String secret, String otpauthUri) {

    public MfaEnrollmentResult(UUID userId, String secret) {
        this(userId, secret, null);
    }
}

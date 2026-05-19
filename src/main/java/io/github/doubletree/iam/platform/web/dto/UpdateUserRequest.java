package io.github.doubletree.iam.platform.web.dto;

import io.github.doubletree.iam.platform.domain.AccountStatus;

public record UpdateUserRequest(
        String displayName,
        String email,
        Boolean emailVerified,
        String phoneNumber,
        Boolean phoneNumberVerified,
        AccountStatus accountStatus) {
}

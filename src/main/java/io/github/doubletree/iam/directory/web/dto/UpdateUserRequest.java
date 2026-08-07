package io.github.doubletree.iam.directory.web.dto;

import io.github.doubletree.iam.directory.domain.AccountStatus;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

public record UpdateUserRequest(
        @Size(min = 1, max = 160) String displayName,
        @Email @Size(max = 254) String email,
        Boolean emailVerified,
        @Size(max = 40) String phoneNumber,
        Boolean phoneNumberVerified,
        AccountStatus accountStatus) {
}

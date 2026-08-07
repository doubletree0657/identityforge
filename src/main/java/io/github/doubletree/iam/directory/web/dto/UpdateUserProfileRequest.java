package io.github.doubletree.iam.directory.web.dto;

import jakarta.validation.constraints.Size;

public record UpdateUserProfileRequest(
        @Size(max = 120) String givenName,
        @Size(max = 120) String familyName,
        @Size(max = 120) String preferredName,
        @Size(max = 32) String locale,
        @Size(max = 80) String timezone,
        @Size(max = 500) String avatarUrl,
        @Size(max = 160) String jobTitle,
        @Size(max = 160) String department,
        @Size(max = 160) String organization,
        @Size(max = 80) String employeeNumber) {
}

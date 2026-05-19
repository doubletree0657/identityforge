package io.github.doubletree.iam.platform.web.dto;

public record UpdateUserProfileRequest(
        String givenName,
        String familyName,
        String preferredName,
        String locale,
        String timezone,
        String avatarUrl,
        String jobTitle,
        String department,
        String organization,
        String employeeNumber) {
}

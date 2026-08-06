package io.github.doubletree.iam.web.dto;

import io.github.doubletree.iam.domain.UserProfile;
import java.time.Instant;
import java.util.UUID;

public record UserProfileResponse(
        UUID id,
        UUID userId,
        String givenName,
        String familyName,
        String preferredName,
        String locale,
        String timezone,
        String avatarUrl,
        String jobTitle,
        String department,
        String organization,
        String employeeNumber,
        Instant createdAt,
        Instant updatedAt) {

    public static UserProfileResponse from(UserProfile profile) {
        return new UserProfileResponse(
                profile.getId(),
                profile.getUser().getId(),
                profile.getGivenName(),
                profile.getFamilyName(),
                profile.getPreferredName(),
                profile.getLocale(),
                profile.getTimezone(),
                profile.getAvatarUrl(),
                profile.getJobTitle(),
                profile.getDepartment(),
                profile.getOrganization(),
                profile.getEmployeeNumber(),
                profile.getCreatedAt(),
                profile.getUpdatedAt());
    }
}

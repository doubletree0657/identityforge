package io.github.doubletree.iam.platform.web.dto;

import io.github.doubletree.iam.platform.domain.AccountStatus;
import io.github.doubletree.iam.platform.domain.User;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public record UserResponse(
        UUID id,
        UUID tenantId,
        String username,
        String displayName,
        String email,
        boolean emailVerified,
        String phoneNumber,
        boolean phoneNumberVerified,
        AccountStatus accountStatus,
        Instant createdAt,
        Instant updatedAt,
        Set<UUID> roleIds) {

    public static UserResponse from(User user) {
        Set<UUID> roleIds = user.getRoles().stream()
                .map(role -> role.getId())
                .collect(Collectors.toSet());
        return new UserResponse(
                user.getId(),
                user.getTenant().getId(),
                user.getUsername(),
                user.getDisplayName(),
                user.getEmail(),
                user.isEmailVerified(),
                user.getPhoneNumber(),
                user.isPhoneNumberVerified(),
                user.getAccountStatus(),
                user.getCreatedAt(),
                user.getUpdatedAt(),
                roleIds);
    }
}

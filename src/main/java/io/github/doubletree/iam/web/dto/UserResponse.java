package io.github.doubletree.iam.web.dto;

import io.github.doubletree.iam.domain.AccountStatus;
import io.github.doubletree.iam.domain.User;
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
        Set<UUID> roleIds,
        Set<UUID> groupRoleIds,
        Set<String> directRoles,
        Set<String> groupRoles,
        Set<String> effectiveRoles,
        Set<String> directPermissions,
        Set<String> groupPermissions,
        Set<String> effectivePermissions) {

    public static UserResponse from(User user) {
        Set<UUID> roleIds = user.getRoles().stream()
                .map(role -> role.getId())
                .collect(Collectors.toSet());
        Set<UUID> groupRoleIds = user.getGroups().stream()
                .flatMap(group -> group.getRoles().stream())
                .map(role -> role.getId())
                .collect(Collectors.toSet());
        Set<String> directRoles = user.getRoles().stream()
                .map(role -> role.getName())
                .collect(Collectors.toSet());
        Set<String> groupRoles = user.getGroups().stream()
                .flatMap(group -> group.getRoles().stream())
                .map(role -> role.getName())
                .collect(Collectors.toSet());
        Set<String> effectiveRoles = new java.util.LinkedHashSet<>(directRoles);
        effectiveRoles.addAll(groupRoles);
        Set<String> directPermissions = user.getRoles().stream()
                .flatMap(role -> role.getPermissions().stream())
                .map(permission -> permission.getName())
                .collect(Collectors.toSet());
        Set<String> groupPermissions = user.getGroups().stream()
                .flatMap(group -> group.getRoles().stream())
                .flatMap(role -> role.getPermissions().stream())
                .map(permission -> permission.getName())
                .collect(Collectors.toSet());
        Set<String> effectivePermissions = new java.util.LinkedHashSet<>(directPermissions);
        effectivePermissions.addAll(groupPermissions);
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
                roleIds,
                groupRoleIds,
                directRoles,
                groupRoles,
                effectiveRoles,
                directPermissions,
                groupPermissions,
                effectivePermissions);
    }
}

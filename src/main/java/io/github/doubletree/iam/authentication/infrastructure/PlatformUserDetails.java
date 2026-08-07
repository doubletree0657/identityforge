package io.github.doubletree.iam.authentication.infrastructure;

import io.github.doubletree.iam.directory.domain.AccountStatus;
import io.github.doubletree.iam.directory.domain.PasswordCredential;
import io.github.doubletree.iam.directory.domain.User;
import io.github.doubletree.iam.directory.domain.TenantStatus;
import io.github.doubletree.iam.directory.api.EffectiveAuthorization;
import io.github.doubletree.iam.directory.application.EffectiveAuthorizationService;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

public record PlatformUserDetails(
        UUID userId,
        UUID tenantId,
        String tenantName,
        String username,
        String displayName,
        String email,
        boolean emailVerified,
        String password,
        AccountStatus accountStatus,
        TenantStatus tenantStatus,
        boolean passwordResetRequired,
        int securityVersion,
        boolean platformOperator,
        Set<String> groups,
        Set<String> directRoles,
        Set<String> groupRoles,
        Set<String> effectiveRoles,
        Set<String> directPermissions,
        Set<String> groupPermissions,
        Set<String> effectivePermissions)
        implements UserDetails {

    public PlatformUserDetails(
            UUID userId,
            UUID tenantId,
            String username,
            String displayName,
            String password,
            AccountStatus accountStatus,
            Set<String> roles,
            Set<String> permissions) {
        this(userId, tenantId, null, username, displayName, null, false, password, accountStatus,
                TenantStatus.ACTIVE, false, 1, false,
                Set.of(), roles, Set.of(), roles, permissions, Set.of(), permissions);
    }

    public static PlatformUserDetails from(
            User user,
            EffectiveAuthorizationService authorizationService,
            boolean platformOperator) {
        PasswordCredential credential = user.getPasswordCredential();
        EffectiveAuthorization authorization = authorizationService.calculate(user);
        return new PlatformUserDetails(
                user.getId(),
                user.getTenant().getId(),
                user.getTenant().getName(),
                user.getUsername(),
                user.getDisplayName(),
                user.getEmail(),
                user.isEmailVerified(),
                credential == null ? null : credential.getPasswordHash(),
                user.getAccountStatus(),
                user.getTenant().getStatus(),
                credential != null && credential.isPasswordResetRequired(),
                credential == null ? 0 : credential.getCredentialsVersion(),
                platformOperator,
                user.getGroups().stream()
                        .map(group -> group.getName())
                        .collect(Collectors.toCollection(LinkedHashSet::new)),
                authorization.directRoles(),
                authorization.groupRoles(),
                authorization.effectiveRoles(),
                authorization.directPermissions(),
                authorization.groupPermissions(),
                authorization.effectivePermissions());
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of();
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return accountStatus != AccountStatus.LOCKED;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return !passwordResetRequired;
    }

    @Override
    public boolean isEnabled() {
        return accountStatus == AccountStatus.ACTIVE
                && tenantStatus == TenantStatus.ACTIVE
                && !passwordResetRequired;
    }

    @Override
    public String toString() {
        return "PlatformUserDetails[userId=%s, tenantId=%s, username=%s, accountStatus=%s]"
                .formatted(userId, tenantId, username, accountStatus);
    }
}

package io.github.doubletree.iam.platform.security.authentication;

import io.github.doubletree.iam.platform.domain.AccountStatus;
import io.github.doubletree.iam.platform.domain.PasswordCredential;
import io.github.doubletree.iam.platform.domain.User;
import io.github.doubletree.iam.platform.application.result.EffectiveAuthorization;
import io.github.doubletree.iam.platform.application.service.EffectiveAuthorizationService;
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
                Set.of(), roles, Set.of(), roles, permissions, Set.of(), permissions);
    }

    public static PlatformUserDetails from(User user, EffectiveAuthorizationService authorizationService) {
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
        return true;
    }

    @Override
    public boolean isEnabled() {
        return accountStatus == AccountStatus.ACTIVE;
    }

    @Override
    public String toString() {
        return "PlatformUserDetails[userId=%s, tenantId=%s, username=%s, accountStatus=%s]"
                .formatted(userId, tenantId, username, accountStatus);
    }
}

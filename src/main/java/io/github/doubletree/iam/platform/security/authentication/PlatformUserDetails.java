package io.github.doubletree.iam.platform.security.authentication;

import io.github.doubletree.iam.platform.domain.AccountStatus;
import io.github.doubletree.iam.platform.domain.PasswordCredential;
import io.github.doubletree.iam.platform.domain.User;
import io.github.doubletree.iam.platform.application.result.EffectiveAuthorization;
import io.github.doubletree.iam.platform.application.service.EffectiveAuthorizationService;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

public record PlatformUserDetails(
        UUID userId,
        UUID tenantId,
        String username,
        String displayName,
        String password,  
        AccountStatus accountStatus,
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
        this(userId, tenantId, username, displayName, password, accountStatus,
                roles, Set.of(), roles, permissions, Set.of(), permissions);
    }

    public static PlatformUserDetails from(User user, EffectiveAuthorizationService authorizationService) {
        PasswordCredential credential = user.getPasswordCredential();
        EffectiveAuthorization authorization = authorizationService.calculate(user);
        return new PlatformUserDetails(
                user.getId(),
                user.getTenant().getId(),
                user.getUsername(),
                user.getDisplayName(),
                credential == null ? null : credential.getPasswordHash(),
                user.getAccountStatus(),
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

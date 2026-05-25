package io.github.doubletree.iam.platform.application.service;

import io.github.doubletree.iam.platform.application.result.EffectiveAuthorization;
import io.github.doubletree.iam.platform.domain.Group;
import io.github.doubletree.iam.platform.domain.Permission;
import io.github.doubletree.iam.platform.domain.Role;
import io.github.doubletree.iam.platform.domain.User;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class EffectiveAuthorizationService {

    public EffectiveAuthorization calculate(User user) {
        Set<Role> directRoles = new LinkedHashSet<>(user.getRoles());
        Set<Role> groupRoles = user.getGroups().stream()
                .flatMap(group -> group.getRoles().stream())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Set<Role> effectiveRoles = new LinkedHashSet<>(directRoles);
        effectiveRoles.addAll(groupRoles);

        Set<String> directPermissions = permissionNames(directRoles);
        Set<String> groupPermissions = permissionNames(groupRoles);
        Set<String> effectivePermissions = new LinkedHashSet<>(directPermissions);
        effectivePermissions.addAll(groupPermissions);

        return new EffectiveAuthorization(
                roleNames(directRoles),
                roleNames(groupRoles),
                roleNames(effectiveRoles),
                directPermissions,
                groupPermissions,
                effectivePermissions);
    }

    private Set<String> roleNames(Set<Role> roles) {
        return roles.stream()
                .map(Role::getName)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private Set<String> permissionNames(Set<Role> roles) {
        return roles.stream()
                .flatMap(role -> role.getPermissions().stream())
                .map(Permission::getName)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }
}

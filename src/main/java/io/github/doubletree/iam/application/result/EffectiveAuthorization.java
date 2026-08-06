package io.github.doubletree.iam.application.result;

import java.util.Set;

public record EffectiveAuthorization(
        Set<String> directRoles,
        Set<String> groupRoles,
        Set<String> effectiveRoles,
        Set<String> directPermissions,
        Set<String> groupPermissions,
        Set<String> effectivePermissions) {
}

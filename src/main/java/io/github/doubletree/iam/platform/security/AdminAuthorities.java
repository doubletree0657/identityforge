package io.github.doubletree.iam.platform.security;

import java.util.Collection;
import java.util.Set;

public final class AdminAuthorities {

    public static final String PLATFORM_ADMIN_ROLE = "platform-admin";
    public static final String TENANT_ADMIN_ROLE = "tenant-admin";
    private static final Set<String> ADMIN_PERMISSIONS = Set.of(
            "iam.admin",
            "iam.admin.read",
            "iam.admin.write",
            "iam.tenants.read",
            "iam.tenants.write",
            "iam.users.read",
            "iam.users.write",
            "iam.groups.read",
            "iam.groups.write",
            "iam.roles.read",
            "iam.roles.write",
            "iam.clients.read",
            "iam.clients.write",
            "iam.audit.read");

    private AdminAuthorities() {
    }

    public static boolean isPlatformAdmin(Collection<String> roles) {
        return roles != null && roles.contains(PLATFORM_ADMIN_ROLE);
    }

    public static boolean isTenantAdmin(Collection<String> roles) {
        return roles != null && roles.contains(TENANT_ADMIN_ROLE);
    }

    public static boolean hasAdminPermission(Collection<String> permissions) {
        return permissions != null && permissions.stream().anyMatch(ADMIN_PERMISSIONS::contains);
    }

    public static boolean hasAdminAccess(Collection<String> roles, Collection<String> permissions) {
        return isPlatformAdmin(roles) || isTenantAdmin(roles) || hasAdminPermission(permissions);
    }
}

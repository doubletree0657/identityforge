package io.github.doubletree.iam.platform.security;

import java.util.Collection;

public final class AdminAuthorities {

    public static final String PLATFORM_ADMIN_ROLE = "platform-admin";
    public static final String TENANT_ADMIN_ROLE = "tenant-admin";

    private AdminAuthorities() {
    }

    public static boolean isPlatformAdmin(Collection<String> roles) {
        return roles != null && roles.contains(PLATFORM_ADMIN_ROLE);
    }

    public static boolean isTenantAdmin(Collection<String> roles) {
        return roles != null && roles.contains(TENANT_ADMIN_ROLE);
    }

    public static boolean hasAdminPermission(Collection<String> permissions) {
        return permissions != null && permissions.stream().anyMatch(BuiltInPermission::isBuiltIn);
    }

    public static boolean hasAdminAccess(Collection<String> roles, Collection<String> permissions) {
        return isPlatformAdmin(roles) || isTenantAdmin(roles) || hasAdminPermission(permissions);
    }
}

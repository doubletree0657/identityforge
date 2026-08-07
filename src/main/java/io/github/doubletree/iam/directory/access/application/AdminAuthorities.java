package io.github.doubletree.iam.directory.access.application;

import java.util.Collection;

public final class AdminAuthorities {

    public static final String TENANT_ADMIN_ROLE = "tenant-admin";

    private AdminAuthorities() {
    }

    public static boolean isTenantAdmin(Collection<String> roles) {
        return roles != null && roles.contains(TENANT_ADMIN_ROLE);
    }

    public static boolean hasAdminPermission(Collection<String> permissions) {
        return permissions != null && permissions.stream().anyMatch(BuiltInPermission::isBuiltIn);
    }

}

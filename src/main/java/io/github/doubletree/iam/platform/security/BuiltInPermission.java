package io.github.doubletree.iam.platform.security;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public enum BuiltInPermission {
    IAM_ADMIN("iam.admin", "Full IAM administration", "Manage every IAM resource across all tenants.", "Administration", true, true, false),
    TENANTS_READ("iam.tenants.read", "Read tenants", "View tenant metadata and lifecycle state.", "Tenants", true, true, true),
    TENANTS_WRITE("iam.tenants.write", "Manage tenants", "Create and update tenant metadata and lifecycle state.", "Tenants", true, false, false),
    USERS_READ("iam.users.read", "Read users", "View users, profiles, attributes, roles, groups, and effective authorization.", "Users", true, true, true),
    USERS_WRITE("iam.users.write", "Manage users", "Create and update users, passwords, statuses, direct roles, and attributes.", "Users", true, true, false),
    GROUPS_READ("iam.groups.read", "Read groups", "View groups, members, and group role assignments.", "Groups", true, true, true),
    GROUPS_WRITE("iam.groups.write", "Manage groups", "Create and update groups, members, and group role assignments.", "Groups", true, true, false),
    ROLES_READ("iam.roles.read", "Read roles", "View roles and role permission assignments.", "Roles", true, true, true),
    ROLES_WRITE("iam.roles.write", "Manage roles", "Create and update roles and role permission assignments.", "Roles", true, true, false),
    PERMISSIONS_READ("iam.permissions.read", "Read permission catalog", "View the system IAM permission catalog.", "Permissions", true, true, true),
    PERMISSIONS_WRITE("iam.permissions.write", "Manage custom permissions", "Create future custom application permissions outside the reserved iam.* namespace.", "Permissions", true, false, false),
    CLIENTS_READ("iam.clients.read", "Read OAuth2 clients", "View OAuth2 client registrations without secret hashes.", "OAuth2 Clients", true, true, true),
    CLIENTS_WRITE("iam.clients.write", "Manage OAuth2 clients", "Create and update OAuth2 clients and rotate confidential client secrets.", "OAuth2 Clients", true, true, false),
    AUDIT_READ("iam.audit.read", "Read audit logs", "View tenant-scoped audit events.", "Audit", true, true, true),
    MFA_MANAGE("iam.mfa.manage", "Manage MFA", "Enroll, verify, and disable user TOTP credentials.", "MFA", true, true, false);

    private final String name;
    private final String displayName;
    private final String description;
    private final String category;
    private final boolean platformAdmin;
    private final boolean tenantAdmin;
    private final boolean auditor;

    BuiltInPermission(
            String name,
            String displayName,
            String description,
            String category,
            boolean platformAdmin,
            boolean tenantAdmin,
            boolean auditor) {
        this.name = name;
        this.displayName = displayName;
        this.description = description;
        this.category = category;
        this.platformAdmin = platformAdmin;
        this.tenantAdmin = tenantAdmin;
        this.auditor = auditor;
    }

    public String permissionName() {
        return name;
    }

    public String displayName() {
        return displayName;
    }

    public String description() {
        return description;
    }

    public String category() {
        return category;
    }

    public static Optional<BuiltInPermission> fromName(String name) {
        return Arrays.stream(values())
                .filter(permission -> permission.name.equals(name))
                .findFirst();
    }

    public static boolean isBuiltIn(String name) {
        return fromName(name).isPresent();
    }

    public static Set<String> allNames() {
        return Arrays.stream(values())
                .map(BuiltInPermission::permissionName)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    public static Set<String> platformAdminNames() {
        return namesFor(permission -> permission.platformAdmin);
    }

    public static Set<String> tenantAdminNames() {
        return namesFor(permission -> permission.tenantAdmin);
    }

    public static Set<String> auditorNames() {
        return namesFor(permission -> permission.auditor);
    }

    private static Set<String> namesFor(java.util.function.Predicate<BuiltInPermission> predicate) {
        return Arrays.stream(values())
                .filter(predicate)
                .map(BuiltInPermission::permissionName)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }
}

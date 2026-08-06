package io.github.doubletree.iam.application.service;

import io.github.doubletree.iam.domain.Permission;
import io.github.doubletree.iam.domain.Role;
import io.github.doubletree.iam.domain.Tenant;
import io.github.doubletree.iam.repository.PermissionRepository;
import io.github.doubletree.iam.repository.RoleRepository;
import io.github.doubletree.iam.security.BuiltInPermission;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SystemPermissionCatalogService {

    public static final String PLATFORM_ADMIN_ROLE_NAME = "platform-admin";
    public static final String TENANT_ADMIN_ROLE_NAME = "tenant-admin";
    public static final String AUDITOR_ROLE_NAME = "auditor";

    private final PermissionRepository permissionRepository;
    private final RoleRepository roleRepository;

    public SystemPermissionCatalogService(PermissionRepository permissionRepository, RoleRepository roleRepository) {
        this.permissionRepository = permissionRepository;
        this.roleRepository = roleRepository;
    }

    @Transactional
    public Map<String, Permission> seedGlobalPermissions() {
        Map<String, Permission> permissions = new LinkedHashMap<>();
        for (BuiltInPermission builtInPermission : BuiltInPermission.values()) {
            Permission permission = permissionRepository.findByName(builtInPermission.permissionName())
                    .orElseGet(() -> Permission.system(
                            builtInPermission.permissionName(),
                            builtInPermission.displayName(),
                            builtInPermission.description(),
                            builtInPermission.category()));
            permission.setDisplayName(builtInPermission.displayName());
            permission.setDescription(builtInPermission.description());
            permission.setCategory(builtInPermission.category());
            permission.setSystemManaged(true);
            permissions.put(builtInPermission.permissionName(), permissionRepository.save(permission));
        }
        return permissions;
    }

    @Transactional
    public void seedRoleTemplates(Tenant tenant) {
        Map<String, Permission> permissions = seedGlobalPermissions();
        initializeRoleTemplate(tenant, PLATFORM_ADMIN_ROLE_NAME, BuiltInPermission.platformAdminNames(), permissions);
        initializeRoleTemplate(tenant, TENANT_ADMIN_ROLE_NAME, BuiltInPermission.tenantAdminNames(), permissions);
        initializeRoleTemplate(tenant, AUDITOR_ROLE_NAME, BuiltInPermission.auditorNames(), permissions);
    }

    private void initializeRoleTemplate(
            Tenant tenant,
            String roleName,
            Set<String> permissionNames,
            Map<String, Permission> permissions) {
        Role role = roleRepository.findByTenantIdAndName(tenant.getId(), roleName)
                .orElseGet(() -> roleRepository.save(Role.create(tenant, roleName)));
        permissionNames.stream()
                .map(permissions::get)
                .forEach(role.getPermissions()::add);
        roleRepository.save(role);
    }
}

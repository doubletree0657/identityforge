package io.github.doubletree.iam.platform.application.service;

import io.github.doubletree.iam.platform.application.exception.EntityNotFoundException;
import io.github.doubletree.iam.platform.application.exception.ValidationException;
import io.github.doubletree.iam.platform.domain.Permission;
import io.github.doubletree.iam.platform.domain.Tenant;
import io.github.doubletree.iam.platform.repository.PermissionRepository;
import io.github.doubletree.iam.platform.repository.TenantRepository;
import io.github.doubletree.iam.platform.security.AdminAuthorizationService;
import io.github.doubletree.iam.platform.security.BuiltInPermission;
import java.util.Locale;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PermissionApplicationService {

    private final PermissionRepository permissionRepository;
    private final TenantRepository tenantRepository;
    private final AuditApplicationService auditApplicationService;
    private final AdminAuthorizationService adminAuthorizationService;
    private final SystemPermissionCatalogService systemPermissionCatalogService;

    public PermissionApplicationService(
            PermissionRepository permissionRepository,
            TenantRepository tenantRepository,
            AuditApplicationService auditApplicationService,
            AdminAuthorizationService adminAuthorizationService,
            SystemPermissionCatalogService systemPermissionCatalogService) {
        this.permissionRepository = permissionRepository;
        this.tenantRepository = tenantRepository;
        this.auditApplicationService = auditApplicationService;
        this.adminAuthorizationService = adminAuthorizationService;
        this.systemPermissionCatalogService = systemPermissionCatalogService;
    }

    @Transactional
    public Permission createPermission(UUID tenantId, String name) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new EntityNotFoundException("Tenant not found: " + tenantId));
        adminAuthorizationService.assertTenantAccess(tenant.getId());
        validateCustomPermissionName(name);

        Permission permission = permissionRepository.save(Permission.create(tenant, name));
        auditApplicationService.recordEvent(tenant.getId(), "PERMISSION_CREATED", "PERMISSION", permission.getId());
        return permission;
    }

    @Transactional(readOnly = true)
    public Page<Permission> listPermissions(UUID tenantId, Pageable pageable) {
        return permissionRepository.findBySystemManagedTrue(pageable);
    }

    @Transactional(readOnly = true)
    public Permission findPermission(UUID permissionId) {
        Permission permission = permissionRepository.findById(permissionId)
                .orElseThrow(() -> new EntityNotFoundException("Permission not found: " + permissionId));
        if (permission.getTenant() != null) {
            adminAuthorizationService.assertTenantAccess(permission.getTenant().getId());
        }
        return permission;
    }

    @Transactional
    public Permission seedBuiltInPermission(BuiltInPermission builtInPermission) {
        return systemPermissionCatalogService.seedGlobalPermissions().get(builtInPermission.permissionName());
    }

    private void validateCustomPermissionName(String name) {
        String normalized = name == null ? "" : name.toLowerCase(Locale.ROOT);
        if (normalized.startsWith("iam.")) {
            throw new ValidationException("Reserved iam.* permissions are system-managed and cannot be created manually");
        }
    }
}

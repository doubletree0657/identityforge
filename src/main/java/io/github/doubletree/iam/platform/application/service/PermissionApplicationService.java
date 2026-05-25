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

    public PermissionApplicationService(
            PermissionRepository permissionRepository,
            TenantRepository tenantRepository,
            AuditApplicationService auditApplicationService,
            AdminAuthorizationService adminAuthorizationService) {
        this.permissionRepository = permissionRepository;
        this.tenantRepository = tenantRepository;
        this.auditApplicationService = auditApplicationService;
        this.adminAuthorizationService = adminAuthorizationService;
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
        UUID allowedTenantId = adminAuthorizationService.tenantIdForList(tenantId);
        if (allowedTenantId == null) {
            return permissionRepository.findAll(pageable);
        }
        return permissionRepository.findByTenantId(allowedTenantId, pageable);
    }

    @Transactional(readOnly = true)
    public Permission findPermission(UUID permissionId) {
        Permission permission = permissionRepository.findById(permissionId)
                .orElseThrow(() -> new EntityNotFoundException("Permission not found: " + permissionId));
        adminAuthorizationService.assertTenantAccess(permission.getTenant().getId());
        return permission;
    }

    @Transactional
    public Permission seedBuiltInPermission(UUID tenantId, BuiltInPermission builtInPermission) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new EntityNotFoundException("Tenant not found: " + tenantId));
        Permission permission = permissionRepository.findByTenantIdAndName(tenantId, builtInPermission.permissionName())
                .orElseGet(() -> Permission.system(
                        tenant,
                        builtInPermission.permissionName(),
                        builtInPermission.displayName(),
                        builtInPermission.description(),
                        builtInPermission.category()));
        permission.setDisplayName(builtInPermission.displayName());
        permission.setDescription(builtInPermission.description());
        permission.setCategory(builtInPermission.category());
        permission.setSystemManaged(true);
        return permissionRepository.save(permission);
    }

    private void validateCustomPermissionName(String name) {
        String normalized = name == null ? "" : name.toLowerCase(Locale.ROOT);
        if (normalized.startsWith("iam.")) {
            throw new ValidationException("Reserved iam.* permissions are system-managed and cannot be created manually");
        }
    }
}

package io.github.doubletree.iam.directory.application;
import io.github.doubletree.iam.audit.application.AuditApplicationService;

import io.github.doubletree.iam.shared.exception.EntityNotFoundException;
import io.github.doubletree.iam.shared.exception.ValidationException;
import io.github.doubletree.iam.directory.domain.Permission;
import io.github.doubletree.iam.directory.infrastructure.persistence.PermissionRepository;
import io.github.doubletree.iam.directory.access.application.BuiltInPermission;
import java.util.Locale;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PermissionApplicationService {

    private final PermissionRepository permissionRepository;
    private final AuditApplicationService auditApplicationService;
    private final SystemPermissionCatalogService systemPermissionCatalogService;

    public PermissionApplicationService(
            PermissionRepository permissionRepository,
            AuditApplicationService auditApplicationService,
            SystemPermissionCatalogService systemPermissionCatalogService) {
        this.permissionRepository = permissionRepository;
        this.auditApplicationService = auditApplicationService;
        this.systemPermissionCatalogService = systemPermissionCatalogService;
    }

    @Transactional
    public Permission createPermission(String name) {
        validateCustomPermissionName(name);

        Permission permission = permissionRepository.save(Permission.create(name));
        auditApplicationService.recordEvent(null, "PERMISSION_CREATED", "PERMISSION", permission.getId());
        return permission;
    }

    @Transactional(readOnly = true)
    public Page<Permission> listPermissions(Pageable pageable) {
        return permissionRepository.findBySystemManagedTrue(pageable);
    }

    @Transactional(readOnly = true)
    public Permission findPermission(UUID permissionId) {
        return permissionRepository.findById(permissionId)
                .orElseThrow(() -> new EntityNotFoundException("Permission not found: " + permissionId));
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

package io.github.doubletree.iam.platform.application.service;

import io.github.doubletree.iam.platform.application.exception.EntityNotFoundException;
import io.github.doubletree.iam.platform.domain.Permission;
import io.github.doubletree.iam.platform.domain.Tenant;
import io.github.doubletree.iam.platform.repository.PermissionRepository;
import io.github.doubletree.iam.platform.repository.TenantRepository;
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

    public PermissionApplicationService(
            PermissionRepository permissionRepository,
            TenantRepository tenantRepository,
            AuditApplicationService auditApplicationService) {
        this.permissionRepository = permissionRepository;
        this.tenantRepository = tenantRepository;
        this.auditApplicationService = auditApplicationService;
    }

    @Transactional
    public Permission createPermission(UUID tenantId, String name) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new EntityNotFoundException("Tenant not found: " + tenantId));

        Permission permission = permissionRepository.save(Permission.create(tenant, name));
        auditApplicationService.recordEvent(tenant.getId(), "PERMISSION_CREATED", "PERMISSION", permission.getId());
        return permission;
    }

    @Transactional(readOnly = true)
    public Page<Permission> listPermissions(UUID tenantId, Pageable pageable) {
        if (tenantId == null) {
            return permissionRepository.findAll(pageable);
        }
        return permissionRepository.findByTenantId(tenantId, pageable);
    }

    @Transactional(readOnly = true)
    public Permission findPermission(UUID permissionId) {
        return permissionRepository.findById(permissionId)
                .orElseThrow(() -> new EntityNotFoundException("Permission not found: " + permissionId));
    }
}

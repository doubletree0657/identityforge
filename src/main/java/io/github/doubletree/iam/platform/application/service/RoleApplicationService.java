package io.github.doubletree.iam.platform.application.service;

import io.github.doubletree.iam.platform.application.exception.EntityNotFoundException;
import io.github.doubletree.iam.platform.domain.Permission;
import io.github.doubletree.iam.platform.domain.Role;
import io.github.doubletree.iam.platform.domain.Tenant;
import io.github.doubletree.iam.platform.repository.PermissionRepository;
import io.github.doubletree.iam.platform.repository.RoleRepository;
import io.github.doubletree.iam.platform.repository.TenantRepository;
import io.github.doubletree.iam.platform.security.AdminAuthorizationService;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RoleApplicationService {

    private final RoleRepository roleRepository;
    private final TenantRepository tenantRepository;
    private final PermissionRepository permissionRepository;
    private final AuditApplicationService auditApplicationService;
    private final AdminAuthorizationService adminAuthorizationService;

    public RoleApplicationService(
            RoleRepository roleRepository,
            TenantRepository tenantRepository,
            PermissionRepository permissionRepository,
            AuditApplicationService auditApplicationService,
            AdminAuthorizationService adminAuthorizationService) {
        this.roleRepository = roleRepository;
        this.tenantRepository = tenantRepository;
        this.permissionRepository = permissionRepository;
        this.auditApplicationService = auditApplicationService;
        this.adminAuthorizationService = adminAuthorizationService;
    }

    @Transactional
    public Role createRole(UUID tenantId, String name) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new EntityNotFoundException("Tenant not found: " + tenantId));
        adminAuthorizationService.assertTenantAccess(tenant.getId());

        Role role = roleRepository.save(Role.create(tenant, name));
        auditApplicationService.recordEvent(tenant.getId(), "ROLE_CREATED", "ROLE", role.getId());
        return role;
    }

    @Transactional(readOnly = true)
    public Page<Role> listRoles(UUID tenantId, Pageable pageable) {
        UUID allowedTenantId = adminAuthorizationService.tenantIdForList(tenantId);
        if (allowedTenantId == null) {
            return roleRepository.findAll(pageable);
        }
        return roleRepository.findByTenantId(allowedTenantId, pageable);
    }

    @Transactional(readOnly = true)
    public Role findRole(UUID roleId) {
        Role role = roleRepository.findById(roleId)
                .orElseThrow(() -> new EntityNotFoundException("Role not found: " + roleId));
        adminAuthorizationService.assertTenantAccess(role.getTenant().getId());
        return role;
    }

    @Transactional
    public Role updateRole(UUID roleId, String name) {
        Role role = findRole(roleId);
        if (name != null) {
            role.setName(name);
        }
        Role savedRole = roleRepository.save(role);
        auditApplicationService.recordEvent(savedRole.getTenant().getId(), "ROLE_UPDATED", "ROLE", savedRole.getId());
        return savedRole;
    }

    @Transactional
    public Role assignPermissionToRole(UUID roleId, UUID permissionId) {
        Role role = roleRepository.findById(roleId)
                .orElseThrow(() -> new EntityNotFoundException("Role not found: " + roleId));
        Permission permission = permissionRepository.findById(permissionId)
                .orElseThrow(() -> new EntityNotFoundException("Permission not found: " + permissionId));

        adminAuthorizationService.assertTenantAccess(role.getTenant().getId());

        role.getPermissions().add(permission);
        Role savedRole = roleRepository.save(role);
        auditApplicationService.recordEvent(
                savedRole.getTenant().getId(), "PERMISSION_ASSIGNED_TO_ROLE", "ROLE", savedRole.getId());
        return savedRole;
    }

    @Transactional
    public Role removePermissionFromRole(UUID roleId, UUID permissionId) {
        Role role = roleRepository.findById(roleId)
                .orElseThrow(() -> new EntityNotFoundException("Role not found: " + roleId));
        Permission permission = permissionRepository.findById(permissionId)
                .orElseThrow(() -> new EntityNotFoundException("Permission not found: " + permissionId));

        adminAuthorizationService.assertTenantAccess(role.getTenant().getId());

        boolean removed = role.getPermissions().remove(permission);
        Role savedRole = roleRepository.save(role);
        if (removed) {
            auditApplicationService.recordEvent(
                    savedRole.getTenant().getId(), "PERMISSION_REMOVED_FROM_ROLE", "ROLE", savedRole.getId());
        }
        return savedRole;
    }
}

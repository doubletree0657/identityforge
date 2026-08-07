package io.github.doubletree.iam.directory.application;
import io.github.doubletree.iam.audit.application.AuditApplicationService;

import io.github.doubletree.iam.shared.exception.EntityNotFoundException;
import io.github.doubletree.iam.directory.domain.Permission;
import io.github.doubletree.iam.directory.domain.Role;
import io.github.doubletree.iam.directory.domain.Tenant;
import io.github.doubletree.iam.directory.infrastructure.persistence.PermissionRepository;
import io.github.doubletree.iam.directory.infrastructure.persistence.RoleRepository;
import io.github.doubletree.iam.directory.infrastructure.persistence.TenantRepository;
import io.github.doubletree.iam.directory.infrastructure.persistence.PasswordCredentialRepository;
import io.github.doubletree.iam.directory.access.application.AdminAuthorizationService;
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
    private final PasswordCredentialRepository passwordCredentialRepository;

    public RoleApplicationService(
            RoleRepository roleRepository,
            TenantRepository tenantRepository,
            PermissionRepository permissionRepository,
            AuditApplicationService auditApplicationService,
            AdminAuthorizationService adminAuthorizationService,
            PasswordCredentialRepository passwordCredentialRepository) {
        this.roleRepository = roleRepository;
        this.tenantRepository = tenantRepository;
        this.permissionRepository = permissionRepository;
        this.auditApplicationService = auditApplicationService;
        this.adminAuthorizationService = adminAuthorizationService;
        this.passwordCredentialRepository = passwordCredentialRepository;
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
        adminAuthorizationService.assertRoleMutable(role);
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
        adminAuthorizationService.assertRoleMutable(role);
        adminAuthorizationService.assertMayDelegatePermission(permission);

        role.getPermissions().add(permission);
        passwordCredentialRepository.incrementVersionForRoleAssignments(role.getId());
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
        adminAuthorizationService.assertRoleMutable(role);

        boolean removed = role.getPermissions().remove(permission);
        if (removed) {
            passwordCredentialRepository.incrementVersionForRoleAssignments(role.getId());
        }
        Role savedRole = roleRepository.save(role);
        if (removed) {
            auditApplicationService.recordEvent(
                    savedRole.getTenant().getId(), "PERMISSION_REMOVED_FROM_ROLE", "ROLE", savedRole.getId());
        }
        return savedRole;
    }
}

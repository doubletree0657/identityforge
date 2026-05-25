package io.github.doubletree.iam.platform.application.service;

import io.github.doubletree.iam.platform.application.exception.EntityNotFoundException;
import io.github.doubletree.iam.platform.application.exception.TenantBoundaryViolationException;
import io.github.doubletree.iam.platform.domain.Group;
import io.github.doubletree.iam.platform.domain.Role;
import io.github.doubletree.iam.platform.domain.Tenant;
import io.github.doubletree.iam.platform.domain.User;
import io.github.doubletree.iam.platform.repository.GroupRepository;
import io.github.doubletree.iam.platform.repository.RoleRepository;
import io.github.doubletree.iam.platform.repository.TenantRepository;
import io.github.doubletree.iam.platform.repository.UserRepository;
import io.github.doubletree.iam.platform.security.AdminAuthorizationService;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GroupApplicationService {

    private final GroupRepository groupRepository;
    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final AuditApplicationService auditApplicationService;
    private final AdminAuthorizationService adminAuthorizationService;

    public GroupApplicationService(
            GroupRepository groupRepository,
            TenantRepository tenantRepository,
            UserRepository userRepository,
            RoleRepository roleRepository,
            AuditApplicationService auditApplicationService,
            AdminAuthorizationService adminAuthorizationService) {
        this.groupRepository = groupRepository;
        this.tenantRepository = tenantRepository;
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.auditApplicationService = auditApplicationService;
        this.adminAuthorizationService = adminAuthorizationService;
    }

    @Transactional
    public Group createGroup(UUID tenantId, String name) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new EntityNotFoundException("Tenant not found: " + tenantId));
        adminAuthorizationService.assertTenantAccess(tenant.getId());

        Group group = groupRepository.save(Group.create(tenant, name));
        auditApplicationService.recordEvent(tenant.getId(), "GROUP_CREATED", "GROUP", group.getId());
        return group;
    }

    @Transactional(readOnly = true)
    public Page<Group> listGroups(UUID tenantId, Pageable pageable) {
        UUID allowedTenantId = adminAuthorizationService.tenantIdForList(tenantId);
        if (allowedTenantId == null) {
            return groupRepository.findAll(pageable);
        }
        return groupRepository.findByTenantId(allowedTenantId, pageable);
    }

    @Transactional(readOnly = true)
    public Group findGroup(UUID groupId) {
        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new EntityNotFoundException("Group not found: " + groupId));
        adminAuthorizationService.assertTenantAccess(group.getTenant().getId());
        return group;
    }

    @Transactional
    public Group updateGroup(UUID groupId, String name, String displayName, String description) {
        Group group = findGroup(groupId);
        if (name != null) {
            group.setName(name);
        }
        if (displayName != null) {
            group.setDisplayName(displayName);
        }
        if (description != null) {
            group.setDescription(description);
        }
        Group savedGroup = groupRepository.save(group);
        auditApplicationService.recordEvent(
                savedGroup.getTenant().getId(), "GROUP_UPDATED", "GROUP", savedGroup.getId());
        return savedGroup;
    }

    @Transactional
    public Group addUserToGroup(UUID groupId, UUID userId) {
        Group group = findGroup(groupId);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("User not found: " + userId));

        adminAuthorizationService.assertSameTenant(
                group.getTenant().getId(), user.getTenant().getId(), "User and group must belong to the same tenant");

        group.addUser(user);
        Group savedGroup = groupRepository.save(group);
        auditApplicationService.recordEvent(
                savedGroup.getTenant().getId(), "USER_ADDED_TO_GROUP", "GROUP", savedGroup.getId());
        return savedGroup;
    }

    @Transactional
    public Group removeUserFromGroup(UUID groupId, UUID userId) {
        Group group = findGroup(groupId);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("User not found: " + userId));

        adminAuthorizationService.assertSameTenant(
                group.getTenant().getId(), user.getTenant().getId(), "User and group must belong to the same tenant");

        boolean removed = group.removeUser(user);
        Group savedGroup = groupRepository.save(group);
        if (removed) {
            auditApplicationService.recordEvent(
                    savedGroup.getTenant().getId(), "USER_REMOVED_FROM_GROUP", "GROUP", savedGroup.getId());
        }
        return savedGroup;
    }

    @Transactional
    public Group assignRoleToGroup(UUID groupId, UUID roleId) {
        Group group = findGroup(groupId);
        Role role = roleRepository.findById(roleId)
                .orElseThrow(() -> new EntityNotFoundException("Role not found: " + roleId));
        adminAuthorizationService.assertSameTenant(
                group.getTenant().getId(), role.getTenant().getId(), "Group and role must belong to the same tenant");

        group.getRoles().add(role);
        Group savedGroup = groupRepository.save(group);
        auditApplicationService.recordEvent(
                savedGroup.getTenant().getId(), "ROLE_ASSIGNED_TO_GROUP", "GROUP", savedGroup.getId());
        return savedGroup;
    }

    @Transactional
    public Group removeRoleFromGroup(UUID groupId, UUID roleId) {
        Group group = findGroup(groupId);
        Role role = roleRepository.findById(roleId)
                .orElseThrow(() -> new EntityNotFoundException("Role not found: " + roleId));
        adminAuthorizationService.assertSameTenant(
                group.getTenant().getId(), role.getTenant().getId(), "Group and role must belong to the same tenant");

        boolean removed = group.getRoles().remove(role);
        Group savedGroup = groupRepository.save(group);
        if (removed) {
            auditApplicationService.recordEvent(
                    savedGroup.getTenant().getId(), "ROLE_REMOVED_FROM_GROUP", "GROUP", savedGroup.getId());
        }
        return savedGroup;
    }
}

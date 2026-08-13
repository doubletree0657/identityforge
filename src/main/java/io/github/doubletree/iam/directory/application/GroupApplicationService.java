package io.github.doubletree.iam.directory.application;
import io.github.doubletree.iam.audit.application.AuditApplicationService;

import io.github.doubletree.iam.shared.exception.EntityNotFoundException;
import io.github.doubletree.iam.shared.exception.TenantBoundaryViolationException;
import io.github.doubletree.iam.directory.domain.Group;
import io.github.doubletree.iam.directory.domain.Role;
import io.github.doubletree.iam.directory.domain.Tenant;
import io.github.doubletree.iam.directory.domain.User;
import io.github.doubletree.iam.directory.infrastructure.persistence.GroupRepository;
import io.github.doubletree.iam.directory.infrastructure.persistence.RoleRepository;
import io.github.doubletree.iam.directory.infrastructure.persistence.TenantRepository;
import io.github.doubletree.iam.directory.infrastructure.persistence.UserRepository;
import io.github.doubletree.iam.directory.infrastructure.persistence.PasswordCredentialRepository;
import io.github.doubletree.iam.directory.access.application.AdminAuthorizationService;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
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
    private final PasswordCredentialRepository passwordCredentialRepository;

    public GroupApplicationService(
            GroupRepository groupRepository,
            TenantRepository tenantRepository,
            UserRepository userRepository,
            RoleRepository roleRepository,
            AuditApplicationService auditApplicationService,
            AdminAuthorizationService adminAuthorizationService,
            PasswordCredentialRepository passwordCredentialRepository) {
        this.groupRepository = groupRepository;
        this.tenantRepository = tenantRepository;
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.auditApplicationService = auditApplicationService;
        this.adminAuthorizationService = adminAuthorizationService;
        this.passwordCredentialRepository = passwordCredentialRepository;
    }

    @Transactional
    public Group createGroup(UUID tenantId, String name) {
        return createGroup(tenantId, name, name, null);
    }

    @Transactional
    public Group createGroup(
            UUID tenantId,
            String name,
            String displayName,
            String description) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new EntityNotFoundException("Tenant not found: " + tenantId));
        adminAuthorizationService.assertTenantAccess(tenant.getId());

        Group candidate = Group.create(tenant, name);
        candidate.setDisplayName(displayName == null ? name : displayName);
        candidate.setDescription(description);
        Group group = groupRepository.save(candidate);
        auditApplicationService.recordEvent(tenant.getId(), "GROUP_CREATED", "GROUP", group.getId());
        return group;
    }

    @Transactional
    public Group createGroupWithMembers(UUID tenantId, String displayName, java.util.List<UUID> memberIds) {
        Group group = createGroup(tenantId, displayName, displayName, null);
        if (memberIds != null) {
            memberIds.forEach(userId -> addUserToGroup(group.getId(), userId));
        }
        return groupRepository.findById(group.getId()).orElse(group);
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
    public Page<Group> listGroupsByDisplayName(UUID tenantId, String displayName, Pageable pageable) {
        return groupRepository.findByTenantIdAndDisplayNameIgnoreCase(
                requireTenantForProvisioning(tenantId), displayName, pageable);
    }

    @Transactional(readOnly = true)
    public Page<Group> listGroupsByMember(UUID tenantId, UUID userId, Pageable pageable) {
        findUserInTenant(tenantId, userId);
        return groupRepository.findDistinctByTenantIdAndMembershipsUserId(
                requireTenantForProvisioning(tenantId), userId, pageable);
    }

    @Transactional(readOnly = true)
    public Group findGroup(UUID groupId) {
        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new EntityNotFoundException("Group not found: " + groupId));
        adminAuthorizationService.assertTenantAccess(group.getTenant().getId());
        return group;
    }

    @Transactional(readOnly = true)
    public Group findGroup(UUID tenantId, UUID groupId) {
        Group group = findGroup(groupId);
        adminAuthorizationService.assertSameTenant(
                tenantId, group.getTenant().getId(), "Group does not belong to the requested tenant");
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
    public Group replaceGroup(UUID tenantId, UUID groupId, String displayName, Set<UUID> memberIds) {
        Group group = findGroup(tenantId, groupId);
        Set<User> desiredUsers = loadUsersInTenant(tenantId, memberIds);
        Set<User> previousUsers = new LinkedHashSet<>(group.getUsers());
        boolean membershipChanged = !previousUsers.equals(desiredUsers);
        group.setName(displayName);
        group.setDisplayName(displayName);
        group.setUsers(desiredUsers);
        invalidateChangedMembers(previousUsers, desiredUsers);
        if (membershipChanged) {
            group.setUpdatedAt(Instant.now());
        }
        Group savedGroup = groupRepository.save(group);
        auditApplicationService.recordEvent(tenantId, "GROUP_UPDATED", "GROUP", savedGroup.getId());
        if (membershipChanged) {
            auditApplicationService.recordEvent(tenantId, "GROUP_MEMBERS_REPLACED", "GROUP", savedGroup.getId());
        }
        return savedGroup;
    }

    @Transactional
    public void deleteGroup(UUID tenantId, UUID groupId) {
        Group group = findGroup(tenantId, groupId);
        group.getUsers().forEach(this::invalidateMembershipForUser);
        groupRepository.delete(group);
        auditApplicationService.recordEvent(tenantId, "GROUP_DELETED", "GROUP", groupId);
    }

    @Transactional
    public Group addUserToGroup(UUID groupId, UUID userId) {
        Group group = findGroup(groupId);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("User not found: " + userId));

        adminAuthorizationService.assertSameTenant(
                group.getTenant().getId(), user.getTenant().getId(), "User and group must belong to the same tenant");

        boolean added = group.addUser(user);
        if (added) {
            invalidateMembershipForUser(user);
            group.setUpdatedAt(Instant.now());
        }
        Group savedGroup = groupRepository.save(group);
        if (added) {
            auditApplicationService.recordEvent(
                    savedGroup.getTenant().getId(), "USER_ADDED_TO_GROUP", "GROUP", savedGroup.getId());
        }
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
        if (removed) {
            invalidateMembershipForUser(user);
            group.setUpdatedAt(Instant.now());
        }
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
        adminAuthorizationService.assertMayDelegateRole(role);

        group.getRoles().add(role);
        passwordCredentialRepository.incrementVersionForGroupMembers(group.getId());
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
        if (removed) {
            passwordCredentialRepository.incrementVersionForGroupMembers(group.getId());
        }
        Group savedGroup = groupRepository.save(group);
        if (removed) {
            auditApplicationService.recordEvent(
                    savedGroup.getTenant().getId(), "ROLE_REMOVED_FROM_GROUP", "GROUP", savedGroup.getId());
        }
        return savedGroup;
    }

    private UUID requireTenantForProvisioning(UUID tenantId) {
        UUID allowedTenantId = adminAuthorizationService.tenantIdForList(tenantId);
        if (allowedTenantId == null) {
            throw new io.github.doubletree.iam.shared.exception.ValidationException(
                    "A tenant is required for provisioning queries");
        }
        return allowedTenantId;
    }

    private User findUserInTenant(UUID tenantId, UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("User not found: " + userId));
        adminAuthorizationService.assertSameTenant(
                tenantId, user.getTenant().getId(), "User does not belong to the requested tenant");
        return user;
    }

    private Set<User> loadUsersInTenant(UUID tenantId, Set<UUID> memberIds) {
        Set<User> users = new LinkedHashSet<>();
        if (memberIds != null) {
            memberIds.forEach(userId -> users.add(findUserInTenant(tenantId, userId)));
        }
        return users;
    }

    private void invalidateChangedMembers(Set<User> previousUsers, Set<User> desiredUsers) {
        Set<User> changed = new LinkedHashSet<>(previousUsers);
        changed.addAll(desiredUsers);
        Set<User> unchanged = new LinkedHashSet<>(previousUsers);
        unchanged.retainAll(desiredUsers);
        changed.removeAll(unchanged);
        changed.forEach(this::invalidateMembershipForUser);
    }

    private void invalidateMembershipForUser(User user) {
        passwordCredentialRepository.incrementVersionForUser(user.getId());
        user.setUpdatedAt(Instant.now());
    }
}

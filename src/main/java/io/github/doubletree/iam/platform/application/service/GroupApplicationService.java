package io.github.doubletree.iam.platform.application.service;

import io.github.doubletree.iam.platform.application.exception.EntityNotFoundException;
import io.github.doubletree.iam.platform.application.exception.TenantBoundaryViolationException;
import io.github.doubletree.iam.platform.domain.Group;
import io.github.doubletree.iam.platform.domain.Tenant;
import io.github.doubletree.iam.platform.domain.User;
import io.github.doubletree.iam.platform.repository.GroupRepository;
import io.github.doubletree.iam.platform.repository.TenantRepository;
import io.github.doubletree.iam.platform.repository.UserRepository;
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
    private final AuditApplicationService auditApplicationService;

    public GroupApplicationService(
            GroupRepository groupRepository,
            TenantRepository tenantRepository,
            UserRepository userRepository,
            AuditApplicationService auditApplicationService) {
        this.groupRepository = groupRepository;
        this.tenantRepository = tenantRepository;
        this.userRepository = userRepository;
        this.auditApplicationService = auditApplicationService;
    }

    @Transactional
    public Group createGroup(UUID tenantId, String name) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new EntityNotFoundException("Tenant not found: " + tenantId));

        Group group = groupRepository.save(Group.create(tenant, name));
        auditApplicationService.recordEvent(tenant.getId(), "GROUP_CREATED", "GROUP", group.getId());
        return group;
    }

    @Transactional(readOnly = true)
    public Page<Group> listGroups(UUID tenantId, Pageable pageable) {
        if (tenantId == null) {
            return groupRepository.findAll(pageable);
        }
        return groupRepository.findByTenantId(tenantId, pageable);
    }

    @Transactional(readOnly = true)
    public Group findGroup(UUID groupId) {
        return groupRepository.findById(groupId)
                .orElseThrow(() -> new EntityNotFoundException("Group not found: " + groupId));
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

        if (!group.getTenant().getId().equals(user.getTenant().getId())) {
            throw new TenantBoundaryViolationException("User and group must belong to the same tenant");
        }

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

        if (!group.getTenant().getId().equals(user.getTenant().getId())) {
            throw new TenantBoundaryViolationException("User and group must belong to the same tenant");
        }

        boolean removed = group.removeUser(user);
        Group savedGroup = groupRepository.save(group);
        if (removed) {
            auditApplicationService.recordEvent(
                    savedGroup.getTenant().getId(), "USER_REMOVED_FROM_GROUP", "GROUP", savedGroup.getId());
        }
        return savedGroup;
    }
}

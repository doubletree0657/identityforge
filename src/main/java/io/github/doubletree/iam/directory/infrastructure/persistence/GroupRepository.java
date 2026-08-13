package io.github.doubletree.iam.directory.infrastructure.persistence;

import io.github.doubletree.iam.directory.domain.Group;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GroupRepository extends JpaRepository<Group, UUID> {

    @Override
    @EntityGraph(attributePaths = {"memberships", "memberships.user", "roles", "roles.permissions"})
    Page<Group> findAll(Pageable pageable);

    @EntityGraph(attributePaths = {"memberships", "memberships.user", "roles", "roles.permissions"})
    Page<Group> findByTenantId(UUID tenantId, Pageable pageable);

    @Override
    @EntityGraph(attributePaths = {"memberships", "memberships.user", "roles", "roles.permissions"})
    java.util.Optional<Group> findById(UUID id);

    @EntityGraph(attributePaths = {"memberships", "memberships.user"})
    Page<Group> findByTenantIdAndDisplayNameIgnoreCase(UUID tenantId, String displayName, Pageable pageable);

    @EntityGraph(attributePaths = {"memberships", "memberships.user"})
    Page<Group> findDistinctByTenantIdAndMembershipsUserId(UUID tenantId, UUID userId, Pageable pageable);
}

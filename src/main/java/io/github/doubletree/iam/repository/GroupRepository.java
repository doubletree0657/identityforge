package io.github.doubletree.iam.repository;

import io.github.doubletree.iam.domain.Group;
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
}

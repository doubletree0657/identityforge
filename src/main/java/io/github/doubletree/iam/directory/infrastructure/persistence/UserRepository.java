package io.github.doubletree.iam.directory.infrastructure.persistence;

import io.github.doubletree.iam.directory.domain.User;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, UUID> {

    @EntityGraph(attributePaths = {
            "roles",
            "tenant",
            "passwordCredential",
            "roles.permissions",
            "groupMemberships",
            "groupMemberships.group",
            "groupMemberships.group.roles",
            "groupMemberships.group.roles.permissions"})
    Optional<User> findByTenantIdAndNormalizedUsername(UUID tenantId, String normalizedUsername);

    @EntityGraph(attributePaths = {
            "tenant",
            "passwordCredential",
            "roles",
            "roles.permissions",
            "groupMemberships",
            "groupMemberships.group",
            "groupMemberships.group.roles",
            "groupMemberships.group.roles.permissions"})
    Optional<User> findByTenantSlugAndNormalizedUsername(String tenantSlug, String normalizedUsername);

    default Optional<User> findByTenantIdAndUsername(UUID tenantId, String username) {
        return findByTenantIdAndNormalizedUsername(
                tenantId, io.github.doubletree.iam.directory.domain.UsernameNormalizer.normalize(username));
    }

    @Override
    @EntityGraph(attributePaths = {
            "tenant",
            "passwordCredential",
            "roles",
            "roles.permissions",
            "groupMemberships",
            "groupMemberships.group",
            "groupMemberships.group.roles",
            "groupMemberships.group.roles.permissions"})
    Optional<User> findById(UUID id);

    @Override
    @EntityGraph(attributePaths = {"roles", "groupMemberships", "groupMemberships.group", "groupMemberships.group.roles"})
    Page<User> findAll(Pageable pageable);

    @EntityGraph(attributePaths = {"roles", "groupMemberships", "groupMemberships.group", "groupMemberships.group.roles"})
    Page<User> findByTenantId(UUID tenantId, Pageable pageable);
}

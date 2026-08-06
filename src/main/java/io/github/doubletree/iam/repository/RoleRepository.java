package io.github.doubletree.iam.repository;

import io.github.doubletree.iam.domain.Role;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoleRepository extends JpaRepository<Role, UUID> {

    @Override
    @EntityGraph(attributePaths = {"permissions"})
    Page<Role> findAll(Pageable pageable);

    @EntityGraph(attributePaths = {"permissions"})
    Page<Role> findByTenantId(UUID tenantId, Pageable pageable);

    @EntityGraph(attributePaths = {"permissions"})
    Optional<Role> findByTenantIdAndName(UUID tenantId, String name);

    @Override
    @EntityGraph(attributePaths = {"permissions"})
    java.util.Optional<Role> findById(UUID id);
}

package io.github.doubletree.iam.platform.repository;

import io.github.doubletree.iam.platform.domain.Role;
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

    @Override
    @EntityGraph(attributePaths = {"permissions"})
    java.util.Optional<Role> findById(UUID id);
}

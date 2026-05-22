package io.github.doubletree.iam.platform.repository;

import io.github.doubletree.iam.platform.domain.User;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, UUID> {

    List<User> findByUsername(String username);

    @EntityGraph(attributePaths = {"roles", "roles.permissions"})
    Optional<User> findByTenantIdAndUsername(UUID tenantId, String username);

    @Override
    @EntityGraph(attributePaths = {"roles"})
    Page<User> findAll(Pageable pageable);

    @EntityGraph(attributePaths = {"roles"})
    Page<User> findByTenantId(UUID tenantId, Pageable pageable);
}

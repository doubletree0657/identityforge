package io.github.doubletree.iam.directory.infrastructure.persistence;

import io.github.doubletree.iam.directory.domain.Permission;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PermissionRepository extends JpaRepository<Permission, UUID> {

    Optional<Permission> findByNameAndSystemManagedTrue(String name);

    Optional<Permission> findByName(String name);

    Page<Permission> findBySystemManagedTrue(Pageable pageable);
}

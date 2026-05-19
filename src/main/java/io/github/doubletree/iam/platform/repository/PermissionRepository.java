package io.github.doubletree.iam.platform.repository;

import io.github.doubletree.iam.platform.domain.Permission;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PermissionRepository extends JpaRepository<Permission, UUID> {

    Page<Permission> findByTenantId(UUID tenantId, Pageable pageable);
}

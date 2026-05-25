package io.github.doubletree.iam.platform.repository;

import io.github.doubletree.iam.platform.domain.ResourcePermission;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ResourcePermissionRepository extends JpaRepository<ResourcePermission, UUID> {

    List<ResourcePermission> findByResourceServerId(UUID resourceServerId);

    Optional<ResourcePermission> findByResourceServerIdAndName(UUID resourceServerId, String name);

    List<ResourcePermission> findByResourceServerIdIn(List<UUID> resourceServerIds);
}

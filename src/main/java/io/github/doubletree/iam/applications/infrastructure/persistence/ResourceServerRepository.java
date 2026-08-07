package io.github.doubletree.iam.applications.infrastructure.persistence;

import io.github.doubletree.iam.applications.domain.ResourceServer;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ResourceServerRepository extends JpaRepository<ResourceServer, UUID> {

    Page<ResourceServer> findByTenantId(UUID tenantId, Pageable pageable);

    Optional<ResourceServer> findByTenantIdAndIdentifier(UUID tenantId, String identifier);
}

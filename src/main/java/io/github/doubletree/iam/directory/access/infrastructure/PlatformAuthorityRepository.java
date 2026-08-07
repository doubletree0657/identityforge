package io.github.doubletree.iam.directory.access.infrastructure;

import io.github.doubletree.iam.directory.access.domain.PlatformAuthority;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlatformAuthorityRepository extends JpaRepository<PlatformAuthority, UUID> {
}

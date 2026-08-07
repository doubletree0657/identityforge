package io.github.doubletree.iam.directory.infrastructure.persistence;

import io.github.doubletree.iam.directory.domain.UserAttribute;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserAttributeRepository extends JpaRepository<UserAttribute, UUID> {

    List<UserAttribute> findByUserIdOrderByNameAsc(UUID userId);

    Optional<UserAttribute> findByUserIdAndName(UUID userId, String name);

    void deleteByUserIdAndName(UUID userId, String name);
}

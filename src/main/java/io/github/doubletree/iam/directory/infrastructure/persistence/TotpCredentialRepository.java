package io.github.doubletree.iam.directory.infrastructure.persistence;

import io.github.doubletree.iam.directory.domain.TotpCredential;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TotpCredentialRepository extends JpaRepository<TotpCredential, UUID> {

    Optional<TotpCredential> findByUserId(UUID userId);

    void deleteByUserId(UUID userId);
}

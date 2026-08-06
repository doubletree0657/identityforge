package io.github.doubletree.iam.repository;

import io.github.doubletree.iam.domain.PasswordCredential;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PasswordCredentialRepository extends JpaRepository<PasswordCredential, UUID> {

    Optional<PasswordCredential> findByUserId(UUID userId);
}

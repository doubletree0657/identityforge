package io.github.doubletree.iam.platform.repository;

import io.github.doubletree.iam.platform.domain.Client;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClientRepository extends JpaRepository<Client, UUID> {

    @EntityGraph(attributePaths = {"redirectUris", "grantTypes", "scopes", "authenticationMethods"})
    List<Client> findAllByClientId(String clientId);

    @Override
    @EntityGraph(attributePaths = {"redirectUris", "grantTypes", "scopes", "authenticationMethods"})
    Optional<Client> findById(UUID id);
}

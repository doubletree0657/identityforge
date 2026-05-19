package io.github.doubletree.iam.platform.repository;

import io.github.doubletree.iam.platform.domain.Client;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClientRepository extends JpaRepository<Client, UUID> {

    @Override
    @EntityGraph(attributePaths = {"redirectUris", "grantTypes", "scopes", "authenticationMethods"})
    Page<Client> findAll(Pageable pageable);

    @EntityGraph(attributePaths = {"redirectUris", "grantTypes", "scopes", "authenticationMethods"})
    List<Client> findAllByClientId(String clientId);

    @Override
    @EntityGraph(attributePaths = {"redirectUris", "grantTypes", "scopes", "authenticationMethods"})
    Optional<Client> findById(UUID id);

    @EntityGraph(attributePaths = {"redirectUris", "grantTypes", "scopes", "authenticationMethods"})
    Page<Client> findByTenantId(UUID tenantId, Pageable pageable);
}

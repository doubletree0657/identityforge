package io.github.doubletree.iam.applications.infrastructure.persistence;

import io.github.doubletree.iam.applications.domain.Client;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClientRepository extends JpaRepository<Client, UUID> {

    @Override
    @EntityGraph(attributePaths = {
            "redirectUris",
            "grantTypes",
            "scopes",
            "authenticationMethods",
            "resourceServer",
            "allowedResourcePermissions",
            "allowedResourcePermissions.resourceServer"
    })
    Page<Client> findAll(Pageable pageable);

    @EntityGraph(attributePaths = {
            "redirectUris",
            "grantTypes",
            "scopes",
            "authenticationMethods",
            "resourceServer",
            "allowedResourcePermissions",
            "allowedResourcePermissions.resourceServer"
    })
    List<Client> findAllByClientId(String clientId);

    @EntityGraph(attributePaths = {
            "tenant",
            "redirectUris",
            "grantTypes",
            "scopes",
            "authenticationMethods",
            "resourceServer",
            "allowedResourcePermissions",
            "allowedResourcePermissions.resourceServer"
    })
    Optional<Client> findByClientId(String clientId);

    boolean existsByClientId(String clientId);

    @EntityGraph(attributePaths = {
            "redirectUris",
            "grantTypes",
            "scopes",
            "authenticationMethods",
            "resourceServer",
            "allowedResourcePermissions",
            "allowedResourcePermissions.resourceServer"
    })
    Optional<Client> findByTenantIdAndClientId(UUID tenantId, String clientId);

    @Override
    @EntityGraph(attributePaths = {
            "redirectUris",
            "grantTypes",
            "scopes",
            "authenticationMethods",
            "resourceServer",
            "allowedResourcePermissions",
            "allowedResourcePermissions.resourceServer"
    })
    Optional<Client> findById(UUID id);

    @EntityGraph(attributePaths = {
            "redirectUris",
            "grantTypes",
            "scopes",
            "authenticationMethods",
            "resourceServer",
            "allowedResourcePermissions",
            "allowedResourcePermissions.resourceServer"
    })
    Page<Client> findByTenantId(UUID tenantId, Pageable pageable);

    @EntityGraph(attributePaths = {"resourceServer"})
    List<Client> findByResourceServerId(UUID resourceServerId);
}

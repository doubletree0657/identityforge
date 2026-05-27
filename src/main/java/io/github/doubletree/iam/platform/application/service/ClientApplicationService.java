package io.github.doubletree.iam.platform.application.service;

import io.github.doubletree.iam.platform.application.exception.ClientValidationException;
import io.github.doubletree.iam.platform.application.exception.EntityNotFoundException;
import io.github.doubletree.iam.platform.application.result.ClientSecretResult;
import io.github.doubletree.iam.platform.domain.Client;
import io.github.doubletree.iam.platform.domain.ClientStatus;
import io.github.doubletree.iam.platform.domain.ClientType;
import io.github.doubletree.iam.platform.domain.ResourcePermission;
import io.github.doubletree.iam.platform.domain.ResourceServer;
import io.github.doubletree.iam.platform.domain.Tenant;
import io.github.doubletree.iam.platform.repository.ClientRepository;
import io.github.doubletree.iam.platform.repository.ResourcePermissionRepository;
import io.github.doubletree.iam.platform.repository.ResourceServerRepository;
import io.github.doubletree.iam.platform.repository.TenantRepository;
import io.github.doubletree.iam.platform.security.AdminAuthorizationService;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ClientApplicationService {

    private final ClientRepository clientRepository;
    private final TenantRepository tenantRepository;
    private final ResourceServerRepository resourceServerRepository;
    private final ResourcePermissionRepository resourcePermissionRepository;
    private final AuditApplicationService auditApplicationService;
    private final PasswordEncoder passwordEncoder;
    private final AdminAuthorizationService adminAuthorizationService;
    private final SecureRandom secureRandom = new SecureRandom();

    public ClientApplicationService(
            ClientRepository clientRepository,
            TenantRepository tenantRepository,
            ResourceServerRepository resourceServerRepository,
            ResourcePermissionRepository resourcePermissionRepository,
            AuditApplicationService auditApplicationService,
            PasswordEncoder passwordEncoder,
            AdminAuthorizationService adminAuthorizationService) {
        this.clientRepository = clientRepository;
        this.tenantRepository = tenantRepository;
        this.resourceServerRepository = resourceServerRepository;
        this.resourcePermissionRepository = resourcePermissionRepository;
        this.auditApplicationService = auditApplicationService;
        this.passwordEncoder = passwordEncoder;
        this.adminAuthorizationService = adminAuthorizationService;
    }

    @Transactional
    public ClientSecretResult createClientWithSecret(
            UUID tenantId,
            String clientId,
            String clientName,
            ClientType clientType,
            Boolean requirePkce,
            Boolean requireConsent,
            Set<String> redirectUris,
            Set<String> grantTypes,
            Set<String> scopes,
            Set<String> authenticationMethods) {
        return createClientWithSecret(
                tenantId,
                clientId,
                clientName,
                clientType,
                requirePkce,
                requireConsent,
                redirectUris,
                grantTypes,
                scopes,
                authenticationMethods,
                null);
    }

    @Transactional
    public ClientSecretResult createClientWithSecret(
            UUID tenantId,
            String clientId,
            String clientName,
            ClientType clientType,
            Boolean requirePkce,
            Boolean requireConsent,
            Set<String> redirectUris,
            Set<String> grantTypes,
            Set<String> scopes,
            Set<String> authenticationMethods,
            UUID resourceServerId) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new EntityNotFoundException("Tenant not found: " + tenantId));
        adminAuthorizationService.assertTenantAccess(tenant.getId());

        Client candidate = Client.create(tenant, clientId, clientName);
        ClientType effectiveClientType = clientType == null ? ClientType.CONFIDENTIAL : clientType;
        configureClientSafely(
                candidate,
                clientName,
                null,
                effectiveClientType,
                requirePkce,
                requireConsent,
                redirectUris,
                grantTypes,
                scopes,
                authenticationMethods);
        candidate.setResourceServer(loadResourceServerForTenant(resourceServerId, tenant.getId()));

        String rawSecret = null;
        if (candidate.getClientType() == ClientType.CONFIDENTIAL
                && usesClientSecretAuthentication(candidate.getAuthenticationMethods())) {
            rawSecret = generateClientSecret();
            candidate.setClientSecretHash(passwordEncoder.encode(rawSecret));
        }

        validateClient(candidate);
        Client client = clientRepository.save(candidate);
        auditApplicationService.recordEvent(tenant.getId(), "CLIENT_CREATED", "CLIENT", client.getId());
        return new ClientSecretResult(client, rawSecret);
    }

    @Transactional(readOnly = true)
    public Page<Client> listClients(UUID tenantId, Pageable pageable) {
        UUID allowedTenantId = adminAuthorizationService.tenantIdForList(tenantId);
        if (allowedTenantId == null) {
            return clientRepository.findAll(pageable);
        }
        return clientRepository.findByTenantId(allowedTenantId, pageable);
    }

    @Transactional(readOnly = true)
    public Client findClient(UUID clientId) {
        return loadClient(clientId);
    }

    @Transactional
    public Client updateClient(
            UUID clientId,
            String clientName,
            ClientStatus status,
            Boolean requirePkce,
            Boolean requireConsent,
            Set<String> redirectUris,
            Set<String> grantTypes,
            Set<String> scopes,
            Set<String> authenticationMethods) {
        return updateClient(
                clientId,
                clientName,
                status,
                requirePkce,
                requireConsent,
                redirectUris,
                grantTypes,
                scopes,
                authenticationMethods,
                null,
                false);
    }

    @Transactional
    public Client updateClient(
            UUID clientId,
            String clientName,
            ClientStatus status,
            Boolean requirePkce,
            Boolean requireConsent,
            Set<String> redirectUris,
            Set<String> grantTypes,
            Set<String> scopes,
            Set<String> authenticationMethods,
            UUID resourceServerId) {
        return updateClient(
                clientId,
                clientName,
                status,
                requirePkce,
                requireConsent,
                redirectUris,
                grantTypes,
                scopes,
                authenticationMethods,
                resourceServerId,
                true);
    }

    @Transactional
    public Client updateClient(
            UUID clientId,
            String clientName,
            ClientStatus status,
            Boolean requirePkce,
            Boolean requireConsent,
            Set<String> redirectUris,
            Set<String> grantTypes,
            Set<String> scopes,
            Set<String> authenticationMethods,
            UUID resourceServerId,
            boolean updateResourceServer) {
        Client client = loadClient(clientId);
        configureClientSafely(
                client,
                clientName,
                status,
                client.getClientType(),
                requirePkce,
                requireConsent,
                redirectUris,
                grantTypes,
                scopes,
                authenticationMethods);
        if (updateResourceServer) {
            ResourceServer resourceServer = loadResourceServerForTenant(resourceServerId, client.getTenant().getId());
            if (resourceServerChanged(client, resourceServer)) {
                client.clearAllowedResourcePermissions();
            }
            client.setResourceServer(resourceServer);
        }
        validateClient(client);
        Client savedClient = clientRepository.save(client);
        auditApplicationService.recordEvent(
                savedClient.getTenant().getId(), "CLIENT_UPDATED", "CLIENT", savedClient.getId());
        return savedClient;
    }

    private boolean resourceServerChanged(Client client, ResourceServer resourceServer) {
        UUID currentResourceServerId = client.getResourceServer() == null ? null : client.getResourceServer().getId();
        UUID nextResourceServerId = resourceServer == null ? null : resourceServer.getId();
        return !java.util.Objects.equals(currentResourceServerId, nextResourceServerId);
    }

    @Transactional(readOnly = true)
    public Set<ResourcePermission> listAllowedResourcePermissions(UUID clientId) {
        return loadClient(clientId).getAllowedResourcePermissions();
    }

    @Transactional
    public Client assignResourcePermissionToClient(UUID clientId, UUID permissionId) {
        Client client = loadClient(clientId);
        ResourcePermission permission = resourcePermissionRepository.findById(permissionId)
                .orElseThrow(() -> new EntityNotFoundException("Resource permission not found: " + permissionId));
        adminAuthorizationService.assertTenantAccess(permission.getResourceServer().getTenant().getId());
        validateResourcePermissionBelongsToClientResourceServer(client, permission);
        client.addAllowedResourcePermission(permission);
        Client savedClient = clientRepository.save(client);
        auditApplicationService.recordEvent(
                savedClient.getTenant().getId(), "CLIENT_RESOURCE_PERMISSION_ASSIGNED", "CLIENT", savedClient.getId());
        return savedClient;
    }

    @Transactional
    public Client removeResourcePermissionFromClient(UUID clientId, UUID permissionId) {
        Client client = loadClient(clientId);
        ResourcePermission permission = resourcePermissionRepository.findById(permissionId)
                .orElseThrow(() -> new EntityNotFoundException("Resource permission not found: " + permissionId));
        validateResourcePermissionBelongsToClientResourceServer(client, permission);
        client.removeAllowedResourcePermission(permission);
        Client savedClient = clientRepository.save(client);
        auditApplicationService.recordEvent(
                savedClient.getTenant().getId(), "CLIENT_RESOURCE_PERMISSION_REMOVED", "CLIENT", savedClient.getId());
        return savedClient;
    }

    @Transactional
    public ClientSecretResult rotateClientSecret(UUID clientId) {
        Client client = loadClient(clientId);
        if (client.getClientType() == ClientType.PUBLIC) {
            throw new ClientValidationException("Public clients do not have client secrets");
        }

        String rawSecret = generateClientSecret();
        client.setClientSecretHash(passwordEncoder.encode(rawSecret));
        validateClient(client);
        Client savedClient = clientRepository.save(client);
        auditApplicationService.recordEvent(
                savedClient.getTenant().getId(), "CLIENT_SECRET_ROTATED", "CLIENT", savedClient.getId());
        return new ClientSecretResult(savedClient, rawSecret);
    }

    private Client loadClient(UUID clientId) {
        Client client = clientRepository.findById(clientId)
                .orElseThrow(() -> new EntityNotFoundException("Client not found: " + clientId));
        adminAuthorizationService.assertTenantAccess(client.getTenant().getId());
        return client;
    }

    private ResourceServer loadResourceServerForTenant(UUID resourceServerId, UUID tenantId) {
        if (resourceServerId == null) {
            return null;
        }
        ResourceServer resourceServer = resourceServerRepository.findById(resourceServerId)
                .orElseThrow(() -> new EntityNotFoundException("Resource server not found: " + resourceServerId));
        adminAuthorizationService.assertTenantAccess(resourceServer.getTenant().getId());
        if (!resourceServer.getTenant().getId().equals(tenantId)) {
            throw new ClientValidationException("Client and resource server must belong to the same tenant");
        }
        return resourceServer;
    }

    private void validateResourcePermissionBelongsToClientResourceServer(Client client, ResourcePermission permission) {
        ResourceServer resourceServer = client.getResourceServer();
        if (resourceServer == null) {
            throw new ClientValidationException("Client must be linked to a resource server before assigning application permissions");
        }
        if (!permission.getResourceServer().getId().equals(resourceServer.getId())) {
            throw new ClientValidationException("Resource permission must belong to the client's resource server");
        }
        if (!permission.getResourceServer().getTenant().getId().equals(client.getTenant().getId())) {
            throw new ClientValidationException("Client and resource permission must belong to the same tenant");
        }
    }

    private void configureClient(
            Client client,
            String clientName,
            ClientStatus status,
            ClientType clientType,
            Boolean requirePkce,
            Boolean requireConsent,
            Set<String> redirectUris,
            Set<String> grantTypes,
            Set<String> scopes,
            Set<String> authenticationMethods) {
        if (clientName != null) {
            client.setClientName(clientName);
        }
        if (status != null) {
            client.setStatus(status);
        }
        if (clientType != null) {
            client.setClientType(clientType);
        }
        if (requirePkce != null) {
            client.setRequirePkce(requirePkce);
        }
        if (requireConsent != null) {
            client.setRequireConsent(requireConsent);
        }
        if (redirectUris != null) {
            client.replaceRedirectUris(copyValues(redirectUris));
        }
        if (grantTypes != null) {
            client.replaceGrantTypes(copyValues(grantTypes));
        }
        if (scopes != null) {
            client.replaceScopes(copyValues(scopes));
        }
        if (authenticationMethods != null) {
            client.replaceAuthenticationMethods(copyValues(authenticationMethods));
        }
        if (client.getClientType() == ClientType.PUBLIC) {
            client.setClientSecretHash(null);
        }
    }

    private void configureClientSafely(
            Client client,
            String clientName,
            ClientStatus status,
            ClientType clientType,
            Boolean requirePkce,
            Boolean requireConsent,
            Set<String> redirectUris,
            Set<String> grantTypes,
            Set<String> scopes,
            Set<String> authenticationMethods) {
        try {
            configureClient(
                    client,
                    clientName,
                    status,
                    clientType,
                    requirePkce,
                    requireConsent,
                    redirectUris,
                    grantTypes,
                    scopes,
                    authenticationMethods);
        } catch (IllegalArgumentException exception) {
            throw new ClientValidationException(exception.getMessage());
        }
    }

    private Set<String> copyValues(Set<String> values) {
        return new LinkedHashSet<>(values);
    }

    private void validateClient(Client client) {
        try {
            client.validateRegistration();
        } catch (IllegalArgumentException exception) {
            throw new ClientValidationException(exception.getMessage());
        }
    }

    private boolean usesClientSecretAuthentication(Set<String> authenticationMethods) {
        return authenticationMethods != null
                && authenticationMethods.stream().anyMatch(method -> method.startsWith("client_secret"));
    }

    private String generateClientSecret() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}

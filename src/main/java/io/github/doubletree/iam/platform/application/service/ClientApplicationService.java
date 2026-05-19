package io.github.doubletree.iam.platform.application.service;

import io.github.doubletree.iam.platform.application.exception.ClientValidationException;
import io.github.doubletree.iam.platform.application.exception.EntityNotFoundException;
import io.github.doubletree.iam.platform.application.result.ClientSecretResult;
import io.github.doubletree.iam.platform.domain.Client;
import io.github.doubletree.iam.platform.domain.ClientStatus;
import io.github.doubletree.iam.platform.domain.ClientType;
import io.github.doubletree.iam.platform.domain.Tenant;
import io.github.doubletree.iam.platform.repository.ClientRepository;
import io.github.doubletree.iam.platform.repository.TenantRepository;
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
    private final AuditApplicationService auditApplicationService;
    private final PasswordEncoder passwordEncoder;
    private final SecureRandom secureRandom = new SecureRandom();

    public ClientApplicationService(
            ClientRepository clientRepository,
            TenantRepository tenantRepository,
            AuditApplicationService auditApplicationService,
            PasswordEncoder passwordEncoder) {
        this.clientRepository = clientRepository;
        this.tenantRepository = tenantRepository;
        this.auditApplicationService = auditApplicationService;
        this.passwordEncoder = passwordEncoder;
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
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new EntityNotFoundException("Tenant not found: " + tenantId));

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
        if (tenantId == null) {
            return clientRepository.findAll(pageable);
        }
        return clientRepository.findByTenantId(tenantId, pageable);
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
        validateClient(client);
        Client savedClient = clientRepository.save(client);
        auditApplicationService.recordEvent(
                savedClient.getTenant().getId(), "CLIENT_UPDATED", "CLIENT", savedClient.getId());
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
        return clientRepository.findById(clientId)
                .orElseThrow(() -> new EntityNotFoundException("Client not found: " + clientId));
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

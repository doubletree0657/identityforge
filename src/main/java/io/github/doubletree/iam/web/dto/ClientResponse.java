package io.github.doubletree.iam.web.dto;

import io.github.doubletree.iam.domain.Client;
import io.github.doubletree.iam.domain.ClientStatus;
import io.github.doubletree.iam.domain.ClientType;
import java.util.Set;
import java.util.UUID;

public record ClientResponse(
        UUID id,
        UUID tenantId,
        String clientId,
        String name,
        ClientType clientType,
        ClientStatus status,
        boolean requirePkce,
        boolean requireConsent,
        UUID resourceServerId,
        String resourceServerName,
        Set<String> redirectUris,
        Set<String> grantTypes,
        Set<String> scopes,
        Set<String> authenticationMethods,
        Set<ResourcePermissionResponse> allowedResourcePermissions) {

    public static ClientResponse from(Client client) {
        return new ClientResponse(
                client.getId(),
                client.getTenant().getId(),
                client.getClientId(),
                client.getClientName(),
                client.getClientType(),
                client.getStatus(),
                client.isRequirePkce(),
                client.isRequireConsent(),
                client.getResourceServer() == null ? null : client.getResourceServer().getId(),
                client.getResourceServer() == null ? null : client.getResourceServer().getName(),
                client.getRedirectUris(),
                client.getGrantTypes(),
                client.getScopes(),
                client.getAuthenticationMethods(),
                client.getAllowedResourcePermissions().stream()
                        .map(ResourcePermissionResponse::from)
                        .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new)));
    }
}

package io.github.doubletree.iam.platform.web.dto;

import io.github.doubletree.iam.platform.domain.ClientType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Set;
import java.util.UUID;

public record CreateClientRequest(
        @NotNull UUID tenantId,
        @NotBlank @Size(max = 120) String clientId,
        @NotBlank @Size(max = 160) String name,
        ClientType clientType,
        Boolean requirePkce,
        Boolean requireConsent,
        Set<String> redirectUris,
        Set<String> grantTypes,
        Set<String> scopes,
        Set<String> authenticationMethods) {
}

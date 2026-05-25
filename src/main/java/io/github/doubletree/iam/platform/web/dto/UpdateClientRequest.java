package io.github.doubletree.iam.platform.web.dto;

import io.github.doubletree.iam.platform.domain.ClientStatus;
import jakarta.validation.constraints.Size;
import java.util.Set;
import java.util.UUID;

public record UpdateClientRequest(
        @Size(min = 1, max = 160) String clientName,
        ClientStatus status,
        Boolean requirePkce,
        Boolean requireConsent,
        Set<String> redirectUris,
        Set<String> grantTypes,
        Set<String> scopes,
        Set<String> authenticationMethods,
        UUID resourceServerId) {
}

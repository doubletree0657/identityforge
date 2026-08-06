package io.github.doubletree.iam.web.dto;

import io.github.doubletree.iam.application.result.OAuth2ConsentView;
import java.util.Set;
import java.util.UUID;

public record OAuth2ConsentResponse(
        UUID userId,
        String username,
        String clientId,
        String clientName,
        Set<String> scopes,
        String resourceServerName) {

    public static OAuth2ConsentResponse from(OAuth2ConsentView consent) {
        return new OAuth2ConsentResponse(
                consent.userId(),
                consent.username(),
                consent.clientId(),
                consent.clientName(),
                consent.scopes(),
                consent.resourceServerName());
    }
}

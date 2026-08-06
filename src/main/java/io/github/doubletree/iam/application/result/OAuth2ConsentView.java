package io.github.doubletree.iam.application.result;

import java.util.Set;
import java.util.UUID;

public record OAuth2ConsentView(
        UUID userId,
        String username,
        String clientId,
        String clientName,
        Set<String> scopes,
        String resourceServerName) {
}

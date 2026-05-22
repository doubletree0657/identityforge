package io.github.doubletree.iam.platform.web.dto;

import java.util.Set;

public record CurrentUserResponse(
        String subject,
        String username,
        String userId,
        String tenantId,
        String displayName,
        Set<String> roles,
        Set<String> scopes) {
}

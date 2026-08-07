package io.github.doubletree.iam.shared.security;

import java.util.Set;
import java.util.UUID;

public record ActorContext(
        ActorType type,
        UUID actorId,
        UUID tenantId,
        boolean platformOperator,
        Set<String> roles,
        Set<String> permissions,
        Set<String> scopes,
        int securityVersion) {

    public ActorContext {
        roles = Set.copyOf(roles == null ? Set.of() : roles);
        permissions = Set.copyOf(permissions == null ? Set.of() : permissions);
        scopes = Set.copyOf(scopes == null ? Set.of() : scopes);
    }

    public static ActorContext system() {
        return new ActorContext(ActorType.SYSTEM, null, null, true, Set.of(), Set.of(), Set.of(), 0);
    }

    public boolean isSystem() {
        return type == ActorType.SYSTEM;
    }
}

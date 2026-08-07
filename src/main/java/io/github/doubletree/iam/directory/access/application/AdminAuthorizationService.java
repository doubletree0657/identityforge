package io.github.doubletree.iam.directory.access.application;

import io.github.doubletree.iam.shared.exception.TenantBoundaryViolationException;
import io.github.doubletree.iam.directory.domain.Permission;
import io.github.doubletree.iam.directory.domain.Role;
import io.github.doubletree.iam.shared.security.ActorContext;
import io.github.doubletree.iam.shared.security.CurrentActor;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

@Service
public class AdminAuthorizationService {

    private final CurrentActor currentActor;

    public AdminAuthorizationService(CurrentActor currentActor) {
        this.currentActor = currentActor;
    }

    public UUID tenantIdForList(UUID requestedTenantId) {
        ActorContext actor = currentActor.get();
        if (actor.isSystem()) {
            return requestedTenantId;
        }
        if (actor.platformOperator()) {
            return requestedTenantId;
        }
        UUID currentTenantId = requireTenant(actor);
        if (requestedTenantId != null && !requestedTenantId.equals(currentTenantId)) {
            throw new AccessDeniedException("Tenant administrators can only access their own tenant");
        }
        return currentTenantId;
    }

    public void assertTenantAccess(UUID tenantId) {
        ActorContext actor = currentActor.get();
        if (actor.isSystem()) {
            return;
        }
        if (tenantId == null || actor.platformOperator()) {
            return;
        }
        if (!tenantId.equals(requireTenant(actor))) {
            throw new AccessDeniedException("Tenant administrators can only access their own tenant");
        }
    }

    public void assertSameTenant(UUID firstTenantId, UUID secondTenantId, String message) {
        if (!firstTenantId.equals(secondTenantId)) {
            throw new TenantBoundaryViolationException(message);
        }
        assertTenantAccess(firstTenantId);
    }

    public boolean isPlatformAdmin() {
        return currentActor.get().platformOperator();
    }

    public void assertMayDelegateRole(Role role) {
        ActorContext actor = currentActor.get();
        if (actor.platformOperator()) {
            return;
        }
        assertTenantAccess(role.getTenant().getId());
        Set<String> actorPermissions = actor.permissions();
        Set<String> delegatedPermissions = role.getPermissions().stream()
                .map(Permission::getName)
                .collect(java.util.stream.Collectors.toSet());
        if (!actorPermissions.containsAll(delegatedPermissions)) {
            throw new AccessDeniedException("A role may only be delegated by an actor holding all of its permissions");
        }
    }

    public void assertMayDelegatePermission(Permission permission) {
        ActorContext actor = currentActor.get();
        if (!actor.platformOperator() && !actor.permissions().contains(permission.getName())) {
            throw new AccessDeniedException("A permission may only be delegated by an actor holding it");
        }
    }

    public void assertRoleMutable(Role role) {
        if (role.isSystemManaged() && !currentActor.get().platformOperator()) {
            throw new AccessDeniedException("System-managed role templates may only be changed by platform operators");
        }
    }

    public ActorContext currentActor() {
        return currentActor.get();
    }

    private UUID requireTenant(ActorContext actor) {
        if (actor.tenantId() == null) {
            throw new AccessDeniedException("Admin token is missing tenant_id");
        }
        return actor.tenantId();
    }
}

package io.github.doubletree.iam.directory.access.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.doubletree.iam.directory.domain.Permission;
import io.github.doubletree.iam.directory.domain.Role;
import io.github.doubletree.iam.directory.domain.Tenant;
import io.github.doubletree.iam.shared.security.ActorContext;
import io.github.doubletree.iam.shared.security.ActorType;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

class AdminAuthorizationServiceTests {

    private final UUID tenantId = UUID.randomUUID();
    private final AtomicReference<ActorContext> actor = new AtomicReference<>();
    private final AdminAuthorizationService service = new AdminAuthorizationService(actor::get);

    @Test
    void platformRoleNameDoesNotCreatePlatformAuthority() {
        actor.set(userActor(tenantId, false, Set.of("platform-admin"), Set.of()));

        assertThat(service.isPlatformAdmin()).isFalse();
        assertThatThrownBy(() -> service.assertTenantAccess(UUID.randomUUID()))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void explicitPlatformAuthorityMayCrossTenantBoundaries() {
        actor.set(userActor(tenantId, true, Set.of(), Set.of()));

        assertThatCode(() -> service.assertTenantAccess(UUID.randomUUID())).doesNotThrowAnyException();
        assertThat(service.isPlatformAdmin()).isTrue();
    }

    @Test
    void tenantActorCanDelegateOnlyPermissionsItAlreadyHolds() {
        Tenant tenant = Tenant.create("Tenant", "tenant");
        tenant.setId(tenantId);
        Permission held = Permission.create("iam.users.read");
        Permission unheld = Permission.create("iam.users.write");
        Role role = Role.create(tenant, "support");
        role.setPermissions(Set.of(held, unheld));
        actor.set(userActor(tenantId, false, Set.of("tenant-admin"), Set.of(held.getName())));

        assertThatCode(() -> service.assertMayDelegatePermission(held)).doesNotThrowAnyException();
        assertThatThrownBy(() -> service.assertMayDelegatePermission(unheld))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.assertMayDelegateRole(role))
                .isInstanceOf(AccessDeniedException.class);
    }

    private ActorContext userActor(
            UUID actorTenantId,
            boolean platformOperator,
            Set<String> roles,
            Set<String> permissions) {
        return new ActorContext(
                ActorType.USER,
                UUID.randomUUID(),
                actorTenantId,
                platformOperator,
                roles,
                permissions,
                Set.of("iam.read", "iam.write"),
                1);
    }
}

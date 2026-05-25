package io.github.doubletree.iam.platform.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import io.github.doubletree.iam.platform.application.exception.ValidationException;
import io.github.doubletree.iam.platform.domain.Permission;
import io.github.doubletree.iam.platform.domain.Tenant;
import io.github.doubletree.iam.platform.repository.PermissionRepository;
import io.github.doubletree.iam.platform.repository.TenantRepository;
import io.github.doubletree.iam.platform.security.AdminAuthorizationService;
import io.github.doubletree.iam.platform.security.BuiltInPermission;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class PermissionApplicationServiceTests {

    private final UUID tenantId = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private final Tenant tenant = tenant("Development Tenant");
    private final PermissionRepository permissionRepository = Mockito.mock(PermissionRepository.class);
    private final TenantRepository tenantRepository = Mockito.mock(TenantRepository.class);
    private final AuditApplicationService auditApplicationService = Mockito.mock(AuditApplicationService.class);
    private final PermissionApplicationService service = new PermissionApplicationService(
            permissionRepository,
            tenantRepository,
            auditApplicationService,
            new AdminAuthorizationService());

    @BeforeEach
    void setUp() {
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));
        when(permissionRepository.save(any(Permission.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void rejectsReservedIamPermissionCreation() {
        assertThatThrownBy(() -> service.createPermission(tenantId, "iam.users.read"))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("system-managed");
    }

    @Test
    void seedsBuiltInPermissionMetadataIdempotently() {
        Permission existing = Permission.create(tenant, BuiltInPermission.USERS_READ.permissionName());
        when(permissionRepository.findByTenantIdAndName(tenantId, BuiltInPermission.USERS_READ.permissionName()))
                .thenReturn(Optional.of(existing));

        Permission permission = service.seedBuiltInPermission(tenantId, BuiltInPermission.USERS_READ);

        assertThat(permission.getName()).isEqualTo("iam.users.read");
        assertThat(permission.getDisplayName()).isEqualTo("Read users");
        assertThat(permission.getCategory()).isEqualTo("Users");
        assertThat(permission.isSystemManaged()).isTrue();
    }

    private Tenant tenant(String name) {
        Tenant tenant = Tenant.create(name);
        tenant.setId(tenantId);
        return tenant;
    }
}

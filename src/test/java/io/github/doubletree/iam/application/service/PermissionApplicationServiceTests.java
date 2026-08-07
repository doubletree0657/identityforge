package io.github.doubletree.iam.application.service;

import io.github.doubletree.iam.audit.application.AuditApplicationService;
import io.github.doubletree.iam.directory.application.PermissionApplicationService;
import io.github.doubletree.iam.directory.application.SystemPermissionCatalogService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import io.github.doubletree.iam.shared.exception.ValidationException;
import io.github.doubletree.iam.directory.domain.Permission;
import io.github.doubletree.iam.directory.infrastructure.persistence.PermissionRepository;
import io.github.doubletree.iam.directory.infrastructure.persistence.RoleRepository;
import io.github.doubletree.iam.directory.access.application.BuiltInPermission;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class PermissionApplicationServiceTests {

    private final PermissionRepository permissionRepository = Mockito.mock(PermissionRepository.class);
    private final RoleRepository roleRepository = Mockito.mock(RoleRepository.class);
    private final AuditApplicationService auditApplicationService = Mockito.mock(AuditApplicationService.class);
    private final SystemPermissionCatalogService systemPermissionCatalogService = new SystemPermissionCatalogService(
            permissionRepository,
            roleRepository);
    private final PermissionApplicationService service = new PermissionApplicationService(
            permissionRepository,
            auditApplicationService,
            systemPermissionCatalogService);

    @BeforeEach
    void setUp() {
        when(permissionRepository.save(any(Permission.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void rejectsReservedIamPermissionCreation() {
        assertThatThrownBy(() -> service.createPermission("iam.users.read"))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("system-managed");
    }

    @Test
    void seedsBuiltInPermissionMetadataIdempotently() {
        Permission existing = Permission.system(BuiltInPermission.USERS_READ.permissionName(), null, null, null);
        when(permissionRepository.findByNameAndSystemManagedTrue(BuiltInPermission.USERS_READ.permissionName()))
                .thenReturn(Optional.of(existing));

        Permission permission = service.seedBuiltInPermission(BuiltInPermission.USERS_READ);

        assertThat(permission.getName()).isEqualTo("iam.users.read");
        assertThat(permission.getDisplayName()).isEqualTo("Read users");
        assertThat(permission.getCategory()).isEqualTo("Users");
        assertThat(permission.isSystemManaged()).isTrue();
    }
}

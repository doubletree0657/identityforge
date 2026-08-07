package io.github.doubletree.iam.directory.access.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.doubletree.iam.audit.application.AuditApplicationService;
import io.github.doubletree.iam.directory.access.infrastructure.PlatformAuthorityRepository;
import io.github.doubletree.iam.directory.infrastructure.persistence.PasswordCredentialRepository;
import io.github.doubletree.iam.shared.security.ActorContext;
import io.github.doubletree.iam.shared.security.ActorType;
import io.github.doubletree.iam.shared.security.CurrentActor;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

class PlatformAuthorityServiceTests {

    private final PlatformAuthorityRepository repository = mock(PlatformAuthorityRepository.class);
    private final PasswordCredentialRepository credentials = mock(PasswordCredentialRepository.class);
    private final AuditApplicationService audit = mock(AuditApplicationService.class);
    private final CurrentActor currentActor = mock(CurrentActor.class);
    private final PlatformAuthorityService service =
            new PlatformAuthorityService(repository, credentials, audit, currentActor);

    @Test
    void tenantActorCannotGrantPlatformAuthority() {
        UUID target = UUID.randomUUID();
        when(currentActor.get()).thenReturn(actor(false));

        assertThatThrownBy(() -> service.grant(target)).isInstanceOf(AccessDeniedException.class);
        verify(repository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void platformGrantAndRevokeInvalidateExistingUserTokensAndAreAudited() {
        UUID target = UUID.randomUUID();
        when(currentActor.get()).thenReturn(actor(true));
        when(repository.existsById(target)).thenReturn(false, true);

        service.grant(target);
        service.revoke(target);

        verify(credentials, org.mockito.Mockito.times(2)).incrementVersionForUser(target);
        verify(audit).recordEvent(null, "PLATFORM_AUTHORITY_GRANTED", "USER", target);
        verify(audit).recordEvent(null, "PLATFORM_AUTHORITY_REVOKED", "USER", target);
    }

    private ActorContext actor(boolean platformOperator) {
        return new ActorContext(
                ActorType.USER,
                UUID.randomUUID(),
                UUID.randomUUID(),
                platformOperator,
                Set.of("tenant-admin"),
                Set.of("iam.admin"),
                Set.of("iam.write"),
                1);
    }
}

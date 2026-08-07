package io.github.doubletree.iam.directory.access.application;

import io.github.doubletree.iam.audit.application.AuditApplicationService;
import io.github.doubletree.iam.directory.access.domain.PlatformAuthority;
import io.github.doubletree.iam.directory.access.infrastructure.PlatformAuthorityRepository;
import io.github.doubletree.iam.directory.infrastructure.persistence.PasswordCredentialRepository;
import io.github.doubletree.iam.shared.security.ActorContext;
import io.github.doubletree.iam.shared.security.CurrentActor;
import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PlatformAuthorityService {

    private final PlatformAuthorityRepository repository;
    private final PasswordCredentialRepository passwordCredentialRepository;
    private final AuditApplicationService auditApplicationService;
    private final CurrentActor currentActor;

    public PlatformAuthorityService(
            PlatformAuthorityRepository repository,
            PasswordCredentialRepository passwordCredentialRepository,
            AuditApplicationService auditApplicationService,
            CurrentActor currentActor) {
        this.repository = repository;
        this.passwordCredentialRepository = passwordCredentialRepository;
        this.auditApplicationService = auditApplicationService;
        this.currentActor = currentActor;
    }

    @Transactional(readOnly = true)
    public boolean isPlatformOperator(UUID userId) {
        return userId != null && repository.existsById(userId);
    }

    @Transactional
    public void grant(UUID userId) {
        ActorContext actor = currentActor.get();
        if (!actor.platformOperator()) {
            throw new AccessDeniedException("Only platform operators can grant platform authority");
        }
        if (!repository.existsById(userId)) {
            repository.save(PlatformAuthority.grant(userId, actor.actorId()));
            passwordCredentialRepository.incrementVersionForUser(userId);
            auditApplicationService.recordEvent(null, "PLATFORM_AUTHORITY_GRANTED", "USER", userId);
        }
    }

    @Transactional
    public void revoke(UUID userId) {
        ActorContext actor = currentActor.get();
        if (!actor.platformOperator()) {
            throw new AccessDeniedException("Only platform operators can revoke platform authority");
        }
        if (repository.existsById(userId)) {
            repository.deleteById(userId);
            passwordCredentialRepository.incrementVersionForUser(userId);
            auditApplicationService.recordEvent(null, "PLATFORM_AUTHORITY_REVOKED", "USER", userId);
        }
    }
}

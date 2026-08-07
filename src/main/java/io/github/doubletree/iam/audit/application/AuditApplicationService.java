package io.github.doubletree.iam.audit.application;

import io.github.doubletree.iam.audit.domain.AuditActorType;
import io.github.doubletree.iam.audit.domain.AuditLog;
import io.github.doubletree.iam.audit.domain.AuditResult;
import io.github.doubletree.iam.audit.infrastructure.AuditLogRepository;
import io.github.doubletree.iam.audit.api.AuditRequestContext;
import io.github.doubletree.iam.audit.api.AuditRequestMetadata;
import io.github.doubletree.iam.shared.security.ActorContext;
import io.github.doubletree.iam.shared.security.ActorType;
import io.github.doubletree.iam.shared.security.CurrentActor;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

@Service
public class AuditApplicationService {

    static final String DEFAULT_ACTOR = "api-client";

    private final AuditLogRepository auditLogRepository;
    private final CurrentActor currentActor;

    public AuditApplicationService(
            AuditLogRepository auditLogRepository,
            org.springframework.beans.factory.ObjectProvider<CurrentActor> currentActor) {
        this.auditLogRepository = auditLogRepository;
        this.currentActor = currentActor.getIfAvailable();
    }

    public AuditLog recordEvent(UUID tenantId, String action, String resourceType, UUID resourceId) {
        return recordEvent(tenantId, action, resourceType, resourceId, AuditResult.SUCCESS, null);
    }

    public AuditLog recordFailure(UUID tenantId, String action, String resourceType, UUID resourceId) {
        return recordFailure(tenantId, action, resourceType, resourceId, null);
    }

    public AuditLog recordFailure(
            UUID tenantId,
            String action,
            String resourceType,
            UUID resourceId,
            String reasonCode) {
        return recordEvent(tenantId, action, resourceType, resourceId, AuditResult.FAILURE, reasonCode);
    }

    private AuditLog recordEvent(
            UUID tenantId,
            String action,
            String resourceType,
            UUID resourceId,
            AuditResult result,
            String reasonCode) {
        ActorContext actor = currentActor == null ? ActorContext.system() : currentActor.get();
        AuditRequestMetadata request = AuditRequestContext.current();
        AuditLog auditLog = AuditLog.record(
                tenantId,
                auditActorType(actor.type()),
                actor.actorId(),
                action,
                resourceType,
                resourceId,
                result);
        auditLog.setSource(request.source());
        auditLog.setCorrelationId(request.correlationId());
        auditLog.setIpAddress(request.ipAddress());
        auditLog.setUserAgent(request.userAgent());
        auditLog.setReasonCode(reasonCode);
        return auditLogRepository.save(auditLog);
    }

    public Page<AuditLog> listAuditLogs(
            UUID tenantId,
            String action,
            String resourceType,
            UUID resourceId,
            io.github.doubletree.iam.audit.domain.AuditResult result,
            Pageable pageable) {
        UUID allowedTenantId = allowedTenantId(tenantId);
        Specification<AuditLog> specification = Specification.allOf(
                equalUuid("tenantId", allowedTenantId),
                equalString("action", action),
                equalString("resourceType", resourceType),
                equalUuid("resourceId", resourceId),
                equalEnum("result", result));
        return auditLogRepository.findAll(specification, pageable);
    }

    private UUID allowedTenantId(UUID requestedTenantId) {
        ActorContext actor = currentActor == null ? ActorContext.system() : currentActor.get();
        if (actor.isSystem() || actor.platformOperator()) {
            return requestedTenantId;
        }
        if (actor.tenantId() == null) {
            throw new org.springframework.security.access.AccessDeniedException("Audit token is missing tenant_id");
        }
        if (requestedTenantId != null && !requestedTenantId.equals(actor.tenantId())) {
            throw new org.springframework.security.access.AccessDeniedException("Audit access is tenant scoped");
        }
        return actor.tenantId();
    }

    private AuditActorType auditActorType(ActorType actorType) {
        return switch (actorType) {
            case USER -> AuditActorType.USER;
            case CLIENT -> AuditActorType.API_CLIENT;
            case SYSTEM -> AuditActorType.SYSTEM;
        };
    }

    private Specification<AuditLog> equalUuid(String fieldName, UUID value) {
        return value == null ? null : (root, query, builder) -> builder.equal(root.get(fieldName), value);
    }

    private Specification<AuditLog> equalString(String fieldName, String value) {
        return value == null || value.isBlank()
                ? null
                : (root, query, builder) -> builder.equal(root.get(fieldName), value);
    }

    private <E extends Enum<E>> Specification<AuditLog> equalEnum(String fieldName, E value) {
        return value == null ? null : (root, query, builder) -> builder.equal(root.get(fieldName), value);
    }
}

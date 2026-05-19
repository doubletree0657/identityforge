package io.github.doubletree.iam.platform.application.service;

import io.github.doubletree.iam.platform.domain.AuditActorType;
import io.github.doubletree.iam.platform.domain.AuditLog;
import io.github.doubletree.iam.platform.domain.AuditResult;
import io.github.doubletree.iam.platform.repository.AuditLogRepository;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

@Service
public class AuditApplicationService {

    static final String DEFAULT_ACTOR = "api-client";

    private final AuditLogRepository auditLogRepository;

    public AuditApplicationService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    public AuditLog recordEvent(UUID tenantId, String action, String resourceType, UUID resourceId) {
        return auditLogRepository.save(AuditLog.record(
                tenantId,
                AuditActorType.API_CLIENT,
                null,
                action,
                resourceType,
                resourceId,
                AuditResult.SUCCESS));
    }

    public Page<AuditLog> listAuditLogs(
            UUID tenantId,
            String action,
            String resourceType,
            UUID resourceId,
            Pageable pageable) {
        Specification<AuditLog> specification = Specification.where(equalUuid("tenantId", tenantId))
                .and(equalString("action", action))
                .and(equalString("resourceType", resourceType))
                .and(equalUuid("resourceId", resourceId));
        return auditLogRepository.findAll(specification, pageable);
    }

    private Specification<AuditLog> equalUuid(String fieldName, UUID value) {
        return value == null ? null : (root, query, builder) -> builder.equal(root.get(fieldName), value);
    }

    private Specification<AuditLog> equalString(String fieldName, String value) {
        return value == null || value.isBlank()
                ? null
                : (root, query, builder) -> builder.equal(root.get(fieldName), value);
    }
}

package io.github.doubletree.iam.platform.web.dto;

import io.github.doubletree.iam.platform.domain.AuditActorType;
import io.github.doubletree.iam.platform.domain.AuditLog;
import io.github.doubletree.iam.platform.domain.AuditResult;
import java.time.Instant;
import java.util.UUID;

public record AuditLogResponse(
        UUID id,
        UUID tenantId,
        AuditActorType actorType,
        UUID actorId,
        String action,
        String resourceType,
        UUID resourceId,
        AuditResult result,
        String ipAddress,
        String userAgent,
        Instant createdAt) {

    public static AuditLogResponse from(AuditLog auditLog) {
        return new AuditLogResponse(
                auditLog.getId(),
                auditLog.getTenantId(),
                auditLog.getActorType(),
                auditLog.getActorId(),
                auditLog.getAction(),
                auditLog.getResourceType(),
                auditLog.getResourceId(),
                auditLog.getResult(),
                auditLog.getIpAddress(),
                auditLog.getUserAgent(),
                auditLog.getCreatedAt());
    }
}

package io.github.doubletree.iam.audit.api;

public record AuditRequestMetadata(
        String source,
        String correlationId,
        String ipAddress,
        String userAgent) {

    public static AuditRequestMetadata system() {
        return new AuditRequestMetadata("SYSTEM", null, null, null);
    }
}

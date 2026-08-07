package io.github.doubletree.iam.audit.api;

public final class AuditRequestContext {

    private static final ThreadLocal<AuditRequestMetadata> CURRENT = new ThreadLocal<>();

    private AuditRequestContext() {
    }

    public static AuditRequestMetadata current() {
        AuditRequestMetadata metadata = CURRENT.get();
        return metadata == null ? AuditRequestMetadata.system() : metadata;
    }

    public static Scope open(AuditRequestMetadata metadata) {
        CURRENT.set(metadata);
        return CURRENT::remove;
    }

    public interface Scope extends AutoCloseable {
        @Override
        void close();
    }
}

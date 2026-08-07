package io.github.doubletree.iam.shared.exception;

public class TenantBoundaryViolationException extends RuntimeException {

    public TenantBoundaryViolationException(String message) {
        super(message);
    }
}

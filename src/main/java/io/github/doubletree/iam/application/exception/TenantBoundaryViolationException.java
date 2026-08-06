package io.github.doubletree.iam.application.exception;

public class TenantBoundaryViolationException extends RuntimeException {

    public TenantBoundaryViolationException(String message) {
        super(message);
    }
}

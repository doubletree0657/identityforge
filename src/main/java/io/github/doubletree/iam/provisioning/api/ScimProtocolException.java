package io.github.doubletree.iam.provisioning.api;

import org.springframework.http.HttpStatus;

public class ScimProtocolException extends RuntimeException {

    private final HttpStatus status;
    private final String scimType;

    public ScimProtocolException(HttpStatus status, String scimType, String detail) {
        super(detail);
        this.status = status;
        this.scimType = scimType;
    }

    public HttpStatus status() {
        return status;
    }

    public String scimType() {
        return scimType;
    }

    public static ScimProtocolException invalidFilter(String detail) {
        return new ScimProtocolException(HttpStatus.BAD_REQUEST, "invalidFilter", detail);
    }

    public static ScimProtocolException invalidPath(String detail) {
        return new ScimProtocolException(HttpStatus.BAD_REQUEST, "invalidPath", detail);
    }

    public static ScimProtocolException invalidValue(String detail) {
        return new ScimProtocolException(HttpStatus.BAD_REQUEST, "invalidValue", detail);
    }

    public static ScimProtocolException preconditionFailed(String detail) {
        return new ScimProtocolException(HttpStatus.PRECONDITION_FAILED, null, detail);
    }
}

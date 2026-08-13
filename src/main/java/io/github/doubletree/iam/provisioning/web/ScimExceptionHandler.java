package io.github.doubletree.iam.provisioning.web;

import io.github.doubletree.iam.audit.application.AuditApplicationService;
import io.github.doubletree.iam.provisioning.api.ScimProtocolException;
import io.github.doubletree.iam.provisioning.web.dto.ScimErrorResponse;
import io.github.doubletree.iam.shared.exception.EntityNotFoundException;
import io.github.doubletree.iam.shared.exception.TenantBoundaryViolationException;
import io.github.doubletree.iam.shared.exception.ValidationException;
import io.github.doubletree.iam.shared.security.ActorContext;
import io.github.doubletree.iam.shared.security.CurrentActor;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = ScimController.class)
public class ScimExceptionHandler {

    private static final MediaType SCIM_MEDIA_TYPE = MediaType.parseMediaType(ScimController.SCIM_MEDIA_TYPE);
    private static final Pattern TENANT_PATH = Pattern.compile("/scim/v2/([0-9a-fA-F-]{36})(?:/|$)");

    private final AuditApplicationService auditApplicationService;
    private final CurrentActor currentActor;

    public ScimExceptionHandler(
            AuditApplicationService auditApplicationService,
            ObjectProvider<CurrentActor> currentActor) {
        this.auditApplicationService = auditApplicationService;
        this.currentActor = currentActor.getIfAvailable();
    }

    @ExceptionHandler(ScimProtocolException.class)
    public ResponseEntity<ScimErrorResponse> handleProtocol(
            ScimProtocolException exception,
            HttpServletRequest request) {
        return error(exception.status(), exception.scimType(), exception.getMessage(), request);
    }

    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<ScimErrorResponse> handleNotFound(
            EntityNotFoundException exception,
            HttpServletRequest request) {
        return error(HttpStatus.NOT_FOUND, null, exception.getMessage(), request);
    }

    @ExceptionHandler({TenantBoundaryViolationException.class, AccessDeniedException.class})
    public ResponseEntity<ScimErrorResponse> handleForbidden(RuntimeException exception, HttpServletRequest request) {
        return error(HttpStatus.FORBIDDEN, null, "The requested tenant resource is not accessible", request);
    }

    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<ScimErrorResponse> handleValidation(
            ValidationException exception,
            HttpServletRequest request) {
        boolean duplicate = exception.getMessage() != null
                && exception.getMessage().toLowerCase(Locale.ROOT).contains("already exists");
        return error(
                duplicate ? HttpStatus.CONFLICT : HttpStatus.BAD_REQUEST,
                duplicate ? "uniqueness" : "invalidValue",
                exception.getMessage(),
                request);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ScimErrorResponse> handleConflict(
            DataIntegrityViolationException exception,
            HttpServletRequest request) {
        return error(HttpStatus.CONFLICT, "uniqueness", "The resource conflicts with an existing value", request);
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ScimErrorResponse> handleConcurrentWrite(
            ObjectOptimisticLockingFailureException exception,
            HttpServletRequest request) {
        return error(HttpStatus.CONFLICT, null, "The resource changed during the request", request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ScimErrorResponse> handleBeanValidation(
            MethodArgumentNotValidException exception,
            HttpServletRequest request) {
        return error(HttpStatus.BAD_REQUEST, "invalidValue", "Request validation failed", request);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ScimErrorResponse> handleUnreadable(
            HttpMessageNotReadableException exception,
            HttpServletRequest request) {
        return error(HttpStatus.BAD_REQUEST, "invalidSyntax", "The SCIM request body is not valid JSON", request);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ScimErrorResponse> handleUnsupportedMediaType(
            HttpMediaTypeNotSupportedException exception,
            HttpServletRequest request) {
        return error(
                HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                "invalidSyntax",
                "Use application/scim+json or application/json for SCIM request bodies",
                request);
    }

    @ExceptionHandler({MissingServletRequestParameterException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ScimErrorResponse> handleInvalidParameter(
            Exception exception,
            HttpServletRequest request) {
        return error(HttpStatus.BAD_REQUEST, "invalidValue", "A SCIM request parameter is invalid", request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ScimErrorResponse> handleUnexpected(
            Exception exception,
            HttpServletRequest request) {
        return error(HttpStatus.INTERNAL_SERVER_ERROR, null, "The SCIM request could not be completed", request);
    }

    private ResponseEntity<ScimErrorResponse> error(
            HttpStatus status,
            String scimType,
            String detail,
            HttpServletRequest request) {
        UUID tenantId = auditTenantId(tenantId(request.getRequestURI()));
        if (tenantId != null) {
            auditApplicationService.recordFailure(
                    tenantId,
                    "SCIM_REQUEST_REJECTED",
                    "TENANT",
                    tenantId,
                    reasonCode(status, scimType));
        }
        return ResponseEntity.status(status)
                .contentType(SCIM_MEDIA_TYPE)
                .body(ScimErrorResponse.of(status.value(), scimType, detail));
    }

    private UUID auditTenantId(UUID requestedTenantId) {
        if (currentActor == null) {
            return requestedTenantId;
        }
        ActorContext actor = currentActor.get();
        if (actor.isSystem() || actor.platformOperator()) {
            return requestedTenantId;
        }
        return actor.tenantId();
    }

    private UUID tenantId(String requestUri) {
        Matcher matcher = TENANT_PATH.matcher(requestUri);
        if (!matcher.find()) {
            return null;
        }
        try {
            return UUID.fromString(matcher.group(1));
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private String reasonCode(HttpStatus status, String scimType) {
        return scimType == null
                ? "HTTP_" + status.value()
                : scimType.replaceAll("([a-z])([A-Z])", "$1_$2").toUpperCase(Locale.ROOT);
    }
}

package io.github.doubletree.iam.platform.web;

import io.github.doubletree.iam.platform.application.service.AuditApplicationService;
import io.github.doubletree.iam.platform.domain.AuditResult;
import io.github.doubletree.iam.platform.web.dto.AuditLogResponse;
import io.github.doubletree.iam.platform.web.dto.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/audit-logs")
@Tag(name = "Audit Logs", description = "Audit log query APIs")
@SecurityRequirement(name = OpenApiConfiguration.BEARER_AUTH)
public class AuditLogController {

    private final AuditApplicationService auditApplicationService;

    public AuditLogController(AuditApplicationService auditApplicationService) {
        this.auditApplicationService = auditApplicationService;
    }

    @GetMapping
    @Operation(summary = "List audit logs", description = "Requires iam.read scope.")
    public PageResponse<AuditLogResponse> listAuditLogs(
            @RequestParam(required = false) UUID tenantId,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String resourceType,
            @RequestParam(required = false) UUID resourceId,
            @RequestParam(required = false) AuditResult result,
            @RequestParam(defaultValue = "" + SafePageRequest.DEFAULT_PAGE) int page,
            @RequestParam(defaultValue = "" + SafePageRequest.DEFAULT_SIZE) int size) {
        return PageResponse.from(
                auditApplicationService.listAuditLogs(
                        tenantId,
                        action,
                        resourceType,
                        resourceId,
                        result,
                        SafePageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"))),
                AuditLogResponse::from);
    }
}

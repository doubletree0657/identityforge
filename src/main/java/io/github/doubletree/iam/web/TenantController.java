package io.github.doubletree.iam.web;

import io.github.doubletree.iam.application.service.TenantApplicationService;
import io.github.doubletree.iam.domain.Tenant;
import io.github.doubletree.iam.web.dto.CreateTenantRequest;
import io.github.doubletree.iam.web.dto.PageResponse;
import io.github.doubletree.iam.web.dto.TenantResponse;
import io.github.doubletree.iam.web.dto.UpdateTenantRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tenants")
@Tag(name = "Tenants", description = "Tenant management APIs")
@SecurityRequirement(name = OpenApiConfiguration.BEARER_AUTH)
public class TenantController {

    private final TenantApplicationService tenantApplicationService;

    public TenantController(TenantApplicationService tenantApplicationService) {
        this.tenantApplicationService = tenantApplicationService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create tenant", description = "Requires iam.write scope.")
    public TenantResponse createTenant(@Valid @RequestBody CreateTenantRequest request) {
        Tenant tenant = tenantApplicationService.createTenant(request.name());
        return TenantResponse.from(tenant);
    }

    @GetMapping
    @Operation(summary = "List tenants", description = "Requires iam.read scope.")
    public PageResponse<TenantResponse> listTenants(
            @RequestParam(defaultValue = "" + SafePageRequest.DEFAULT_PAGE) int page,
            @RequestParam(defaultValue = "" + SafePageRequest.DEFAULT_SIZE) int size) {
        return PageResponse.from(tenantApplicationService.listTenants(SafePageRequest.of(page, size)), TenantResponse::from);
    }

    @GetMapping("/{tenantId}")
    @Operation(summary = "Get tenant", description = "Requires iam.read scope.")
    public TenantResponse getTenant(@PathVariable UUID tenantId) {
        return TenantResponse.from(tenantApplicationService.findTenant(tenantId));
    }

    @PutMapping("/{tenantId}")
    @Operation(summary = "Update tenant", description = "Requires iam.write scope.")
    public TenantResponse updateTenant(
            @PathVariable UUID tenantId,
            @Valid @RequestBody UpdateTenantRequest request) {
        return TenantResponse.from(tenantApplicationService.updateTenant(
                tenantId,
                request.name(),
                request.slug(),
                request.status()));
    }
}

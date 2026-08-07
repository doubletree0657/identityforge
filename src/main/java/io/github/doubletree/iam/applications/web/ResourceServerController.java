package io.github.doubletree.iam.applications.web;
import io.github.doubletree.iam.shared.web.SafePageRequest;
import io.github.doubletree.iam.shared.web.OpenApiConfiguration;

import io.github.doubletree.iam.applications.application.ResourceServerApplicationService;
import io.github.doubletree.iam.applications.domain.ResourcePermission;
import io.github.doubletree.iam.applications.domain.ResourceServer;
import io.github.doubletree.iam.applications.web.dto.CreateResourcePermissionRequest;
import io.github.doubletree.iam.applications.web.dto.CreateResourceServerRequest;
import io.github.doubletree.iam.shared.web.PageResponse;
import io.github.doubletree.iam.applications.web.dto.ResourcePermissionResponse;
import io.github.doubletree.iam.applications.web.dto.ResourceServerResponse;
import io.github.doubletree.iam.applications.web.dto.UpdateResourcePermissionRequest;
import io.github.doubletree.iam.applications.web.dto.UpdateResourceServerRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/resource-servers")
@Tag(name = "Resource Servers", description = "Tenant application and resource server APIs")
@SecurityRequirement(name = OpenApiConfiguration.BEARER_AUTH)
public class ResourceServerController {

    private final ResourceServerApplicationService resourceServerApplicationService;

    public ResourceServerController(ResourceServerApplicationService resourceServerApplicationService) {
        this.resourceServerApplicationService = resourceServerApplicationService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create resource server", description = "Requires iam.write scope.")
    public ResourceServerResponse createResourceServer(@Valid @RequestBody CreateResourceServerRequest request) {
        return ResourceServerResponse.from(resourceServerApplicationService.createResourceServer(
                request.tenantId(),
                request.identifier(),
                request.name(),
                request.description()));
    }

    @GetMapping
    @Operation(summary = "List resource servers", description = "Requires iam.read scope.")
    public PageResponse<ResourceServerResponse> listResourceServers(
            @RequestParam(required = false) UUID tenantId,
            @RequestParam(defaultValue = "" + SafePageRequest.DEFAULT_PAGE) int page,
            @RequestParam(defaultValue = "" + SafePageRequest.DEFAULT_SIZE) int size) {
        return PageResponse.from(
                resourceServerApplicationService.listResourceServers(tenantId, SafePageRequest.of(page, size)),
                ResourceServerResponse::from);
    }

    @GetMapping("/{resourceServerId}")
    @Operation(summary = "Get resource server", description = "Requires iam.read scope.")
    public ResourceServerResponse getResourceServer(@PathVariable UUID resourceServerId) {
        return ResourceServerResponse.from(resourceServerApplicationService.findResourceServer(resourceServerId));
    }

    @PutMapping("/{resourceServerId}")
    @Operation(summary = "Update resource server", description = "Requires iam.write scope.")
    public ResourceServerResponse updateResourceServer(
            @PathVariable UUID resourceServerId,
            @Valid @RequestBody UpdateResourceServerRequest request) {
        return ResourceServerResponse.from(resourceServerApplicationService.updateResourceServer(
                resourceServerId,
                request.identifier(),
                request.name(),
                request.description(),
                request.status()));
    }

    @PostMapping("/{resourceServerId}/disable")
    @Operation(summary = "Disable resource server", description = "Requires iam.write scope.")
    public ResourceServerResponse disableResourceServer(@PathVariable UUID resourceServerId) {
        return ResourceServerResponse.from(resourceServerApplicationService.disableResourceServer(resourceServerId));
    }

    @PostMapping("/{resourceServerId}/reactivate")
    @Operation(summary = "Reactivate resource server", description = "Requires iam.write scope.")
    public ResourceServerResponse reactivateResourceServer(@PathVariable UUID resourceServerId) {
        return ResourceServerResponse.from(resourceServerApplicationService.reactivateResourceServer(resourceServerId));
    }

    @GetMapping("/{resourceServerId}/permissions")
    @Operation(summary = "List resource permissions", description = "Requires iam.read scope.")
    public List<ResourcePermissionResponse> listResourcePermissions(@PathVariable UUID resourceServerId) {
        return resourceServerApplicationService.listResourcePermissions(resourceServerId).stream()
                .map(ResourcePermissionResponse::from)
                .toList();
    }

    @PostMapping("/{resourceServerId}/permissions")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create resource permission", description = "Requires iam.write scope.")
    public ResourcePermissionResponse createResourcePermission(
            @PathVariable UUID resourceServerId,
            @Valid @RequestBody CreateResourcePermissionRequest request) {
        ResourcePermission permission = resourceServerApplicationService.createResourcePermission(
                resourceServerId,
                request.name(),
                request.displayName(),
                request.description());
        return ResourcePermissionResponse.from(permission);
    }

    @PutMapping("/{resourceServerId}/permissions/{permissionId}")
    @Operation(summary = "Update resource permission", description = "Requires iam.write scope.")
    public ResourcePermissionResponse updateResourcePermission(
            @PathVariable UUID resourceServerId,
            @PathVariable UUID permissionId,
            @Valid @RequestBody UpdateResourcePermissionRequest request) {
        return ResourcePermissionResponse.from(resourceServerApplicationService.updateResourcePermission(
                resourceServerId,
                permissionId,
                request.name(),
                request.displayName(),
                request.description()));
    }
}

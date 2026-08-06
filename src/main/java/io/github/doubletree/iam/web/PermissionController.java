package io.github.doubletree.iam.web;

import io.github.doubletree.iam.application.service.PermissionApplicationService;
import io.github.doubletree.iam.domain.Permission;
import io.github.doubletree.iam.web.dto.CreatePermissionRequest;
import io.github.doubletree.iam.web.dto.PageResponse;
import io.github.doubletree.iam.web.dto.PermissionResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/permissions")
@Tag(name = "Permissions", description = "Permission management APIs")
@SecurityRequirement(name = OpenApiConfiguration.BEARER_AUTH)
public class PermissionController {

    private final PermissionApplicationService permissionApplicationService;

    public PermissionController(PermissionApplicationService permissionApplicationService) {
        this.permissionApplicationService = permissionApplicationService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create permission", description = "Requires iam.write scope.")
    public PermissionResponse createPermission(@Valid @RequestBody CreatePermissionRequest request) {
        Permission permission = permissionApplicationService.createPermission(request.name());
        return PermissionResponse.from(permission);
    }

    @GetMapping
    @Operation(summary = "List permissions", description = "Requires iam.read scope.")
    public PageResponse<PermissionResponse> listPermissions(
            @RequestParam(defaultValue = "" + SafePageRequest.DEFAULT_PAGE) int page,
            @RequestParam(defaultValue = "" + SafePageRequest.DEFAULT_SIZE) int size) {
        return PageResponse.from(permissionApplicationService.listPermissions(SafePageRequest.of(page, size)), PermissionResponse::from);
    }

    @GetMapping("/{permissionId}")
    @Operation(summary = "Get permission", description = "Requires iam.read scope.")
    public PermissionResponse getPermission(@PathVariable UUID permissionId) {
        return PermissionResponse.from(permissionApplicationService.findPermission(permissionId));
    }
}

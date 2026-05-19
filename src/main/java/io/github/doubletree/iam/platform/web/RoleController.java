package io.github.doubletree.iam.platform.web;

import io.github.doubletree.iam.platform.application.service.RoleApplicationService;
import io.github.doubletree.iam.platform.domain.Role;
import io.github.doubletree.iam.platform.web.dto.CreateRoleRequest;
import io.github.doubletree.iam.platform.web.dto.RoleResponse;
import io.github.doubletree.iam.platform.web.dto.UpdateRoleRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
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
@RequestMapping("/api/roles")
@Tag(name = "Roles", description = "Role management APIs")
@SecurityRequirement(name = OpenApiConfiguration.BEARER_AUTH)
public class RoleController {

    private final RoleApplicationService roleApplicationService;

    public RoleController(RoleApplicationService roleApplicationService) {
        this.roleApplicationService = roleApplicationService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create role", description = "Requires iam.write scope.")
    public RoleResponse createRole(@Valid @RequestBody CreateRoleRequest request) {
        Role role = roleApplicationService.createRole(request.tenantId(), request.name());
        return RoleResponse.from(role);
    }

    @GetMapping
    @Operation(summary = "List roles", description = "Requires iam.read scope.")
    public List<RoleResponse> listRoles(
            @RequestParam(required = false) UUID tenantId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return roleApplicationService.listRoles(tenantId, PageRequest.of(page, size)).stream()
                .map(RoleResponse::from)
                .toList();
    }

    @GetMapping("/{roleId}")
    @Operation(summary = "Get role", description = "Requires iam.read scope.")
    public RoleResponse getRole(@PathVariable UUID roleId) {
        return RoleResponse.from(roleApplicationService.findRole(roleId));
    }

    @PutMapping("/{roleId}")
    @Operation(summary = "Update role", description = "Requires iam.write scope.")
    public RoleResponse updateRole(@PathVariable UUID roleId, @RequestBody UpdateRoleRequest request) {
        return RoleResponse.from(roleApplicationService.updateRole(roleId, request.name()));
    }

    @PostMapping("/{roleId}/permissions/{permissionId}")
    @Operation(summary = "Assign permission to role", description = "Requires iam.write scope.")
    public RoleResponse assignPermissionToRole(@PathVariable UUID roleId, @PathVariable UUID permissionId) {
        Role role = roleApplicationService.assignPermissionToRole(roleId, permissionId);
        return RoleResponse.from(role);
    }

    @DeleteMapping("/{roleId}/permissions/{permissionId}")
    @Operation(summary = "Remove permission from role", description = "Requires iam.write scope.")
    public RoleResponse removePermissionFromRole(@PathVariable UUID roleId, @PathVariable UUID permissionId) {
        Role role = roleApplicationService.removePermissionFromRole(roleId, permissionId);
        return RoleResponse.from(role);
    }
}

package io.github.doubletree.iam.platform.web;

import io.github.doubletree.iam.platform.application.service.GroupApplicationService;
import io.github.doubletree.iam.platform.domain.Group;
import io.github.doubletree.iam.platform.web.dto.CreateGroupRequest;
import io.github.doubletree.iam.platform.web.dto.GroupResponse;
import io.github.doubletree.iam.platform.web.dto.PageResponse;
import io.github.doubletree.iam.platform.web.dto.RoleResponse;
import io.github.doubletree.iam.platform.web.dto.UpdateGroupRequest;
import io.github.doubletree.iam.platform.web.dto.UserResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
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
@RequestMapping("/api/groups")
@Tag(name = "Groups", description = "Group management APIs")
@SecurityRequirement(name = OpenApiConfiguration.BEARER_AUTH)
public class GroupController {

    private final GroupApplicationService groupApplicationService;

    public GroupController(GroupApplicationService groupApplicationService) {
        this.groupApplicationService = groupApplicationService;
    }

    @GetMapping
    @Operation(summary = "List groups", description = "Requires iam.read scope.")
    public PageResponse<GroupResponse> listGroups(
            @RequestParam(required = false) UUID tenantId,
            @RequestParam(defaultValue = "" + SafePageRequest.DEFAULT_PAGE) int page,
            @RequestParam(defaultValue = "" + SafePageRequest.DEFAULT_SIZE) int size) {
        return PageResponse.from(groupApplicationService.listGroups(tenantId, SafePageRequest.of(page, size)), GroupResponse::from);
    }

    @GetMapping("/{groupId}")
    @Operation(summary = "Get group", description = "Requires iam.read scope.")
    public GroupResponse getGroup(@PathVariable UUID groupId) {
        return GroupResponse.from(groupApplicationService.findGroup(groupId));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create group", description = "Requires iam.write scope.")
    public GroupResponse createGroup(@Valid @RequestBody CreateGroupRequest request) {
        Group group = groupApplicationService.createGroup(request.tenantId(), request.name());
        group = groupApplicationService.updateGroup(group.getId(), null, request.displayName(), request.description());
        return GroupResponse.from(group);
    }

    @PutMapping("/{groupId}")
    @Operation(summary = "Update group", description = "Requires iam.write scope.")
    public GroupResponse updateGroup(@PathVariable UUID groupId, @Valid @RequestBody UpdateGroupRequest request) {
        return GroupResponse.from(groupApplicationService.updateGroup(
                groupId,
                request.name(),
                request.displayName(),
                request.description()));
    }

    @PostMapping("/{groupId}/members/{userId}")
    @Operation(summary = "Add user to group", description = "Requires iam.write scope.")
    public GroupResponse addUserToGroup(@PathVariable UUID groupId, @PathVariable UUID userId) {
        return GroupResponse.from(groupApplicationService.addUserToGroup(groupId, userId));
    }

    @DeleteMapping("/{groupId}/members/{userId}")
    @Operation(summary = "Remove user from group", description = "Requires iam.write scope.")
    public GroupResponse removeUserFromGroup(@PathVariable UUID groupId, @PathVariable UUID userId) {
        return GroupResponse.from(groupApplicationService.removeUserFromGroup(groupId, userId));
    }

    @GetMapping("/{groupId}/members")
    @Operation(summary = "List group members", description = "Requires iam.read scope.")
    public List<UserResponse> listGroupMembers(@PathVariable UUID groupId) {
        return groupApplicationService.findGroup(groupId).getUsers().stream()
                .map(UserResponse::from)
                .toList();
    }

    @GetMapping("/{groupId}/roles")
    @Operation(summary = "List group roles", description = "Requires iam.read scope and admin authorization.")
    public List<RoleResponse> listGroupRoles(@PathVariable UUID groupId) {
        return groupApplicationService.findGroup(groupId).getRoles().stream()
                .map(RoleResponse::from)
                .toList();
    }

    @PostMapping("/{groupId}/roles/{roleId}")
    @Operation(summary = "Assign role to group", description = "Requires iam.write scope and admin authorization.")
    public GroupResponse assignRoleToGroup(@PathVariable UUID groupId, @PathVariable UUID roleId) {
        return GroupResponse.from(groupApplicationService.assignRoleToGroup(groupId, roleId));
    }

    @DeleteMapping("/{groupId}/roles/{roleId}")
    @Operation(summary = "Remove role from group", description = "Requires iam.write scope and admin authorization.")
    public GroupResponse removeRoleFromGroup(@PathVariable UUID groupId, @PathVariable UUID roleId) {
        return GroupResponse.from(groupApplicationService.removeRoleFromGroup(groupId, roleId));
    }
}

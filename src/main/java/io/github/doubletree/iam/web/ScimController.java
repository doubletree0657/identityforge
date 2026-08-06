package io.github.doubletree.iam.web;

import io.github.doubletree.iam.application.service.GroupApplicationService;
import io.github.doubletree.iam.application.service.UserApplicationService;
import io.github.doubletree.iam.domain.Group;
import io.github.doubletree.iam.domain.User;
import io.github.doubletree.iam.web.dto.ScimCreateGroupRequest;
import io.github.doubletree.iam.web.dto.ScimCreateUserRequest;
import io.github.doubletree.iam.web.dto.ScimGroupResponse;
import io.github.doubletree.iam.web.dto.ScimUserResponse;
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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/scim/v2")
@Tag(name = "SCIM", description = "Minimal SCIM-style provisioning APIs")
@SecurityRequirement(name = OpenApiConfiguration.BEARER_AUTH)
public class ScimController {

    private final UserApplicationService userApplicationService;
    private final GroupApplicationService groupApplicationService;

    public ScimController(
            UserApplicationService userApplicationService,
            GroupApplicationService groupApplicationService) {
        this.userApplicationService = userApplicationService;
        this.groupApplicationService = groupApplicationService;
    }

    @PostMapping("/Users")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create SCIM user", description = "Requires iam.write scope.")
    public ScimUserResponse createUser(@Valid @RequestBody ScimCreateUserRequest request) {
        User user = userApplicationService.createUser(request.tenantId(), request.userName(), request.displayName());
        return ScimUserResponse.from(user);
    }

    @GetMapping("/Users/{id}")
    @Operation(summary = "Get SCIM user", description = "Requires iam.read scope.")
    public ScimUserResponse getUser(@PathVariable UUID id) {
        return ScimUserResponse.from(userApplicationService.findUser(id));
    }

    @PostMapping("/Groups")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create SCIM group", description = "Requires iam.write scope.")
    public ScimGroupResponse createGroup(@Valid @RequestBody ScimCreateGroupRequest request) {
        Group group = groupApplicationService.createGroup(request.tenantId(), request.displayName());
        if (request.members() != null) {
            for (UUID memberId : request.members()) {
                group = groupApplicationService.addUserToGroup(group.getId(), memberId);
            }
        }
        return ScimGroupResponse.from(group);
    }

    @GetMapping("/Groups/{id}")
    @Operation(summary = "Get SCIM group", description = "Requires iam.read scope.")
    public ScimGroupResponse getGroup(@PathVariable UUID id) {
        return ScimGroupResponse.from(groupApplicationService.findGroup(id));
    }
}

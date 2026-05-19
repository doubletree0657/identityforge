package io.github.doubletree.iam.platform.web;

import io.github.doubletree.iam.platform.application.service.UserApplicationService;
import io.github.doubletree.iam.platform.domain.User;
import io.github.doubletree.iam.platform.web.dto.CreateUserRequest;
import io.github.doubletree.iam.platform.web.dto.UpdateUserPasswordRequest;
import io.github.doubletree.iam.platform.web.dto.UserResponse;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
@Tag(name = "Users", description = "User management APIs")
@SecurityRequirement(name = OpenApiConfiguration.BEARER_AUTH)
public class UserController {

    private final UserApplicationService userApplicationService;

    public UserController(UserApplicationService userApplicationService) {
        this.userApplicationService = userApplicationService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create user", description = "Requires iam.write scope.")
    public UserResponse createUser(@Valid @RequestBody CreateUserRequest request) {
        User user = userApplicationService.createUser(request.tenantId(), request.username(), request.displayName());
        return UserResponse.from(user);
    }

    @GetMapping("/{userId}")
    @Operation(summary = "Get user", description = "Requires iam.read scope.")
    public UserResponse getUser(@PathVariable UUID userId) {
        User user = userApplicationService.findUser(userId);
        return UserResponse.from(user);
    }

    @PostMapping("/{userId}/roles/{roleId}")
    @Operation(summary = "Assign role to user", description = "Requires iam.write scope.")
    public UserResponse assignRoleToUser(@PathVariable UUID userId, @PathVariable UUID roleId) {
        User user = userApplicationService.assignRoleToUser(userId, roleId);
        return UserResponse.from(user);
    }

    @PutMapping("/{userId}/password")
    @Operation(summary = "Update user password", description = "Requires iam.write scope.")
    public UserResponse updatePassword(
            @PathVariable UUID userId,
            @Valid @RequestBody UpdateUserPasswordRequest request) {
        User user = userApplicationService.updatePassword(userId, request.newPassword());
        if (Boolean.TRUE.equals(request.passwordResetRequired())) {
            user = userApplicationService.requirePasswordReset(userId);
        }
        return UserResponse.from(user);
    }
}

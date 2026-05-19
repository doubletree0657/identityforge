package io.github.doubletree.iam.platform.web;

import io.github.doubletree.iam.platform.application.service.UserApplicationService;
import io.github.doubletree.iam.platform.domain.User;
import io.github.doubletree.iam.platform.domain.UserAttribute;
import io.github.doubletree.iam.platform.domain.UserProfile;
import io.github.doubletree.iam.platform.web.dto.CreateUserRequest;
import io.github.doubletree.iam.platform.web.dto.SetUserAttributeRequest;
import io.github.doubletree.iam.platform.web.dto.UpdateUserProfileRequest;
import io.github.doubletree.iam.platform.web.dto.UpdateUserPasswordRequest;
import io.github.doubletree.iam.platform.web.dto.UpdateUserRequest;
import io.github.doubletree.iam.platform.web.dto.UserAttributeResponse;
import io.github.doubletree.iam.platform.web.dto.UserProfileResponse;
import io.github.doubletree.iam.platform.web.dto.UserResponse;
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

    @GetMapping
    @Operation(summary = "List users", description = "Requires iam.read scope.")
    public List<UserResponse> listUsers(
            @RequestParam(required = false) UUID tenantId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return userApplicationService.listUsers(tenantId, PageRequest.of(page, size)).stream()
                .map(UserResponse::from)
                .toList();
    }

    @GetMapping("/{userId}")
    @Operation(summary = "Get user", description = "Requires iam.read scope.")
    public UserResponse getUser(@PathVariable UUID userId) {
        User user = userApplicationService.findUser(userId);
        return UserResponse.from(user);
    }

    @PutMapping("/{userId}")
    @Operation(summary = "Update user", description = "Requires iam.write scope.")
    public UserResponse updateUser(@PathVariable UUID userId, @RequestBody UpdateUserRequest request) {
        User user = userApplicationService.updateUser(
                userId,
                request.displayName(),
                request.email(),
                request.emailVerified(),
                request.phoneNumber(),
                request.phoneNumberVerified(),
                request.accountStatus());
        return UserResponse.from(user);
    }

    @PostMapping("/{userId}/roles/{roleId}")
    @Operation(summary = "Assign role to user", description = "Requires iam.write scope.")
    public UserResponse assignRoleToUser(@PathVariable UUID userId, @PathVariable UUID roleId) {
        User user = userApplicationService.assignRoleToUser(userId, roleId);
        return UserResponse.from(user);
    }

    @DeleteMapping("/{userId}/roles/{roleId}")
    @Operation(summary = "Remove role from user", description = "Requires iam.write scope.")
    public UserResponse removeRoleFromUser(@PathVariable UUID userId, @PathVariable UUID roleId) {
        User user = userApplicationService.removeRoleFromUser(userId, roleId);
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

    @GetMapping("/{userId}/profile")
    @Operation(summary = "Get user profile", description = "Requires iam.read scope.")
    public UserProfileResponse getProfile(@PathVariable UUID userId) {
        return UserProfileResponse.from(userApplicationService.findProfileByUserId(userId));
    }

    @PutMapping("/{userId}/profile")
    @Operation(summary = "Update user profile", description = "Requires iam.write scope.")
    public UserProfileResponse updateProfile(
            @PathVariable UUID userId,
            @RequestBody UpdateUserProfileRequest request) {
        UserProfile profile = userApplicationService.updateProfile(
                userId,
                request.givenName(),
                request.familyName(),
                request.preferredName(),
                request.locale(),
                request.timezone(),
                request.avatarUrl(),
                request.jobTitle(),
                request.department(),
                request.organization(),
                request.employeeNumber());
        return UserProfileResponse.from(profile);
    }

    @GetMapping("/{userId}/attributes")
    @Operation(summary = "List user attributes", description = "Requires iam.read scope.")
    public List<UserAttributeResponse> listAttributes(@PathVariable UUID userId) {
        return userApplicationService.listAttributes(userId).stream()
                .map(UserAttributeResponse::from)
                .toList();
    }

    @PutMapping("/{userId}/attributes/{name}")
    @Operation(summary = "Set user attribute", description = "Requires iam.write scope.")
    public UserAttributeResponse setAttribute(
            @PathVariable UUID userId,
            @PathVariable String name,
            @Valid @RequestBody SetUserAttributeRequest request) {
        UserAttribute attribute = userApplicationService.setAttribute(
                userId,
                name,
                request.value(),
                request.valueType());
        return UserAttributeResponse.from(attribute);
    }

    @DeleteMapping("/{userId}/attributes/{name}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete user attribute", description = "Requires iam.write scope.")
    public void deleteAttribute(@PathVariable UUID userId, @PathVariable String name) {
        userApplicationService.deleteAttribute(userId, name);
    }
}

package io.github.doubletree.iam.provisioning.web;

import io.github.doubletree.iam.directory.domain.Group;
import io.github.doubletree.iam.directory.domain.User;
import io.github.doubletree.iam.provisioning.application.ScimProvisioningService;
import io.github.doubletree.iam.provisioning.web.dto.ScimGroupRequest;
import io.github.doubletree.iam.provisioning.web.dto.ScimGroupResponse;
import io.github.doubletree.iam.provisioning.web.dto.ScimListResponse;
import io.github.doubletree.iam.provisioning.web.dto.ScimPatchRequest;
import io.github.doubletree.iam.provisioning.web.dto.ScimServiceProviderConfigResponse;
import io.github.doubletree.iam.provisioning.web.dto.ScimUserRequest;
import io.github.doubletree.iam.provisioning.web.dto.ScimUserResponse;
import io.github.doubletree.iam.shared.web.OpenApiConfiguration;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping(value = "/scim/v2/{tenantId}", produces = ScimController.SCIM_MEDIA_TYPE)
@Tag(name = "SCIM 2.0", description = "Documented SCIM 2.0 supported subset")
@SecurityRequirement(name = OpenApiConfiguration.BEARER_AUTH)
public class ScimController {

    public static final String SCIM_MEDIA_TYPE = "application/scim+json";

    private final ScimProvisioningService provisioningService;

    public ScimController(ScimProvisioningService provisioningService) {
        this.provisioningService = provisioningService;
    }

    @GetMapping("/ServiceProviderConfig")
    @Operation(summary = "Describe the supported SCIM capabilities")
    public ScimServiceProviderConfigResponse serviceProviderConfig(@PathVariable UUID tenantId) {
        provisioningService.assertTenantAccess(tenantId);
        return ScimServiceProviderConfigResponse.supportedSubset();
    }

    @PostMapping(value = "/Users", consumes = {SCIM_MEDIA_TYPE, "application/json"})
    @Operation(summary = "Create a SCIM User")
    public ResponseEntity<ScimUserResponse> createUser(
            @PathVariable UUID tenantId,
            @Valid @RequestBody ScimUserRequest request) {
        return created(userResponse(tenantId, provisioningService.createUser(tenantId, request)));
    }

    @GetMapping("/Users")
    @Operation(summary = "List or filter SCIM Users")
    public ScimListResponse<ScimUserResponse> listUsers(
            @PathVariable UUID tenantId,
            @RequestParam(required = false) String filter,
            @RequestParam(defaultValue = "1") int startIndex,
            @RequestParam(defaultValue = "50") int count) {
        var page = provisioningService.listUsers(tenantId, filter, startIndex, count);
        return ScimListResponse.of(
                page.totalResults(),
                startIndex,
                count,
                page.resources().stream().map(user -> userResponse(tenantId, user)).toList());
    }

    @GetMapping("/Users/{id}")
    @Operation(summary = "Get a SCIM User")
    public ResponseEntity<ScimUserResponse> getUser(@PathVariable UUID tenantId, @PathVariable UUID id) {
        return ok(userResponse(tenantId, provisioningService.getUser(tenantId, id)));
    }

    @PutMapping(value = "/Users/{id}", consumes = {SCIM_MEDIA_TYPE, "application/json"})
    @Operation(summary = "Replace a SCIM User")
    public ResponseEntity<ScimUserResponse> replaceUser(
            @PathVariable UUID tenantId,
            @PathVariable UUID id,
            @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch,
            @Valid @RequestBody ScimUserRequest request) {
        return ok(userResponse(tenantId, provisioningService.replaceUser(tenantId, id, request, ifMatch)));
    }

    @PatchMapping(value = "/Users/{id}", consumes = {SCIM_MEDIA_TYPE, "application/json"})
    @Operation(summary = "Patch supported SCIM User attributes")
    public ResponseEntity<ScimUserResponse> patchUser(
            @PathVariable UUID tenantId,
            @PathVariable UUID id,
            @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch,
            @Valid @RequestBody ScimPatchRequest request) {
        return ok(userResponse(tenantId, provisioningService.patchUser(tenantId, id, request, ifMatch)));
    }

    @DeleteMapping("/Users/{id}")
    @Operation(summary = "Delete a SCIM User")
    public ResponseEntity<Void> deleteUser(
            @PathVariable UUID tenantId,
            @PathVariable UUID id,
            @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch) {
        provisioningService.deleteUser(tenantId, id, ifMatch);
        return ResponseEntity.noContent().build();
    }

    @PostMapping(value = "/Groups", consumes = {SCIM_MEDIA_TYPE, "application/json"})
    @Operation(summary = "Create a SCIM Group and optional direct memberships")
    public ResponseEntity<ScimGroupResponse> createGroup(
            @PathVariable UUID tenantId,
            @Valid @RequestBody ScimGroupRequest request) {
        return created(groupResponse(tenantId, provisioningService.createGroup(tenantId, request)));
    }

    @GetMapping("/Groups")
    @Operation(summary = "List or filter SCIM Groups")
    public ScimListResponse<ScimGroupResponse> listGroups(
            @PathVariable UUID tenantId,
            @RequestParam(required = false) String filter,
            @RequestParam(defaultValue = "1") int startIndex,
            @RequestParam(defaultValue = "50") int count) {
        var page = provisioningService.listGroups(tenantId, filter, startIndex, count);
        return ScimListResponse.of(
                page.totalResults(),
                startIndex,
                count,
                page.resources().stream().map(group -> groupResponse(tenantId, group)).toList());
    }

    @GetMapping("/Groups/{id}")
    @Operation(summary = "Get a SCIM Group")
    public ResponseEntity<ScimGroupResponse> getGroup(@PathVariable UUID tenantId, @PathVariable UUID id) {
        return ok(groupResponse(tenantId, provisioningService.getGroup(tenantId, id)));
    }

    @PutMapping(value = "/Groups/{id}", consumes = {SCIM_MEDIA_TYPE, "application/json"})
    @Operation(summary = "Replace a SCIM Group and its direct memberships")
    public ResponseEntity<ScimGroupResponse> replaceGroup(
            @PathVariable UUID tenantId,
            @PathVariable UUID id,
            @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch,
            @Valid @RequestBody ScimGroupRequest request) {
        return ok(groupResponse(tenantId, provisioningService.replaceGroup(tenantId, id, request, ifMatch)));
    }

    @PatchMapping(value = "/Groups/{id}", consumes = {SCIM_MEDIA_TYPE, "application/json"})
    @Operation(summary = "Patch a SCIM Group or its direct memberships")
    public ResponseEntity<ScimGroupResponse> patchGroup(
            @PathVariable UUID tenantId,
            @PathVariable UUID id,
            @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch,
            @Valid @RequestBody ScimPatchRequest request) {
        return ok(groupResponse(tenantId, provisioningService.patchGroup(tenantId, id, request, ifMatch)));
    }

    @DeleteMapping("/Groups/{id}")
    @Operation(summary = "Delete a SCIM Group")
    public ResponseEntity<Void> deleteGroup(
            @PathVariable UUID tenantId,
            @PathVariable UUID id,
            @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch) {
        provisioningService.deleteGroup(tenantId, id, ifMatch);
        return ResponseEntity.noContent().build();
    }

    private ScimUserResponse userResponse(UUID tenantId, User user) {
        return ScimUserResponse.from(user, locationPrefix(tenantId));
    }

    private ScimGroupResponse groupResponse(UUID tenantId, Group group) {
        return ScimGroupResponse.from(group, locationPrefix(tenantId));
    }

    private String locationPrefix(UUID tenantId) {
        return ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/scim/v2/{tenantId}")
                .buildAndExpand(tenantId)
                .toUriString();
    }

    private <T> ResponseEntity<T> created(T body) {
        String location = body instanceof ScimUserResponse user
                ? user.meta().location()
                : ((ScimGroupResponse) body).meta().location();
        String version = body instanceof ScimUserResponse user
                ? user.meta().version()
                : ((ScimGroupResponse) body).meta().version();
        return ResponseEntity.created(URI.create(location)).eTag(version).body(body);
    }

    private <T> ResponseEntity<T> ok(T body) {
        String version = body instanceof ScimUserResponse user
                ? user.meta().version()
                : ((ScimGroupResponse) body).meta().version();
        return ResponseEntity.ok().eTag(version).body(body);
    }
}

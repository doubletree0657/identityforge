package io.github.doubletree.iam.platform.web;

import io.github.doubletree.iam.platform.application.service.OAuth2ConsentApplicationService;
import io.github.doubletree.iam.platform.web.dto.OAuth2ConsentResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/oauth2/consents")
@Tag(name = "OAuth2 Consents", description = "Safe OAuth2 consent listing and revocation APIs")
@SecurityRequirement(name = OpenApiConfiguration.BEARER_AUTH)
public class OAuth2ConsentController {

    private final OAuth2ConsentApplicationService consentApplicationService;

    public OAuth2ConsentController(OAuth2ConsentApplicationService consentApplicationService) {
        this.consentApplicationService = consentApplicationService;
    }

    @GetMapping
    @Operation(summary = "List OAuth2 consents", description = "Requires iam.read scope and Admin RBAC.")
    public List<OAuth2ConsentResponse> listConsents(@RequestParam(required = false) UUID userId) {
        return consentApplicationService.listConsents(userId).stream()
                .map(OAuth2ConsentResponse::from)
                .toList();
    }

    @GetMapping("/me")
    @Operation(summary = "List current user OAuth2 consents", description = "Requires iam.read scope.")
    public List<OAuth2ConsentResponse> listCurrentUserConsents(@AuthenticationPrincipal Jwt jwt) {
        return consentApplicationService.listConsentsForCurrentUser(jwt).stream()
                .map(OAuth2ConsentResponse::from)
                .toList();
    }

    @DeleteMapping("/{clientId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Revoke a user's OAuth2 consent", description = "Requires iam.write scope and Admin RBAC.")
    public void revokeConsent(
            @PathVariable String clientId,
            @RequestParam UUID userId) {
        consentApplicationService.revokeConsent(userId, clientId);
    }

    @DeleteMapping("/me/{clientId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Revoke current user OAuth2 consent", description = "Requires iam.write scope.")
    public void revokeCurrentUserConsent(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String clientId) {
        consentApplicationService.revokeCurrentUserConsent(jwt, clientId);
    }
}

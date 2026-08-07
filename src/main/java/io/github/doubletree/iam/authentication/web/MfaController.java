package io.github.doubletree.iam.authentication.web;
import io.github.doubletree.iam.shared.web.OpenApiConfiguration;

import io.github.doubletree.iam.authentication.application.MfaApplicationService;
import io.github.doubletree.iam.authentication.web.dto.MfaEnrollmentResponse;
import io.github.doubletree.iam.authentication.web.dto.MfaStatusResponse;
import io.github.doubletree.iam.authentication.web.dto.TotpVerificationRequest;
import io.github.doubletree.iam.authentication.web.dto.TotpVerificationResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users/{userId}/mfa/totp")
@Tag(name = "MFA", description = "TOTP MFA management APIs")
@SecurityRequirement(name = OpenApiConfiguration.BEARER_AUTH)
public class MfaController {

    private final MfaApplicationService mfaApplicationService;

    public MfaController(MfaApplicationService mfaApplicationService) {
        this.mfaApplicationService = mfaApplicationService;
    }

    @PostMapping("/enrollment")
    @Operation(summary = "Enroll TOTP", description = "Requires iam.write scope. Returns the setup secret once.")
    public MfaEnrollmentResponse enrollTotp(@PathVariable UUID userId) {
        return MfaEnrollmentResponse.from(mfaApplicationService.enrollTotp(userId));
    }

    @PostMapping("/verification")
    @Operation(summary = "Verify TOTP", description = "Requires iam.write scope. Does not return the TOTP secret.")
    public TotpVerificationResponse verifyTotp(
            @PathVariable UUID userId,
            @Valid @RequestBody TotpVerificationRequest request) {
        return new TotpVerificationResponse(userId, mfaApplicationService.verifyTotp(userId, request.code()));
    }

    @DeleteMapping
    @Operation(summary = "Disable TOTP", description = "Requires iam.write scope.")
    public MfaStatusResponse disableTotp(@PathVariable UUID userId) {
        mfaApplicationService.disableTotp(userId);
        return new MfaStatusResponse(userId, false);
    }
}

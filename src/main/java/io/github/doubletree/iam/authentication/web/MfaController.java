package io.github.doubletree.iam.authentication.web;
import io.github.doubletree.iam.shared.web.OpenApiConfiguration;

import io.github.doubletree.iam.authentication.application.MfaApplicationService;
import io.github.doubletree.iam.authentication.web.dto.MfaEnrollmentResponse;
import io.github.doubletree.iam.authentication.web.dto.MfaStatusResponse;
import io.github.doubletree.iam.authentication.web.dto.MfaRecoveryCodesResponse;
import io.github.doubletree.iam.authentication.web.dto.TotpVerificationRequest;
import io.github.doubletree.iam.authentication.web.dto.TotpVerificationResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
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
    public ResponseEntity<MfaEnrollmentResponse> enrollTotp(@PathVariable UUID userId) {
        return noStore(MfaEnrollmentResponse.from(mfaApplicationService.enrollTotp(userId)));
    }

    @PostMapping("/verification")
    @Operation(summary = "Verify TOTP", description = "Requires iam.write scope. Initial verification returns a one-time recovery-code set, but never the TOTP secret.")
    public ResponseEntity<TotpVerificationResponse> verifyTotp(
            @PathVariable UUID userId,
            @Valid @RequestBody TotpVerificationRequest request) {
        return noStore(TotpVerificationResponse.from(mfaApplicationService.verifyTotp(userId, request.code())));
    }

    @GetMapping
    @Operation(summary = "Get MFA status", description = "Requires iam.read scope. Returns state and recovery-code counts only.")
    public MfaStatusResponse status(@PathVariable UUID userId) {
        return MfaStatusResponse.from(mfaApplicationService.getStatus(userId));
    }

    @PostMapping("/recovery-codes")
    @Operation(summary = "Regenerate recovery codes", description = "Requires iam.write scope and self-service access. Replaces all previous recovery codes and returns the new set once.")
    public ResponseEntity<MfaRecoveryCodesResponse> regenerateRecoveryCodes(@PathVariable UUID userId) {
        return noStore(MfaRecoveryCodesResponse.from(mfaApplicationService.regenerateRecoveryCodes(userId)));
    }

    @DeleteMapping
    @Operation(summary = "Disable TOTP", description = "Requires iam.write scope.")
    public MfaStatusResponse disableTotp(@PathVariable UUID userId) {
        mfaApplicationService.disableTotp(userId);
        return MfaStatusResponse.from(mfaApplicationService.getStatus(userId));
    }

    private <T> ResponseEntity<T> noStore(T body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);
    }
}

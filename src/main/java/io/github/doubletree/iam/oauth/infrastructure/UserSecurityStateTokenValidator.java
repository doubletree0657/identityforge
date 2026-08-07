package io.github.doubletree.iam.oauth.infrastructure;

import io.github.doubletree.iam.authentication.application.UserSecurityStateService;
import java.util.UUID;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

public class UserSecurityStateTokenValidator implements OAuth2TokenValidator<Jwt> {

    private static final OAuth2Error INVALID_STATE = new OAuth2Error(
            "invalid_token", "User or tenant security state has changed", null);
    private final UserSecurityStateService securityStateService;

    public UserSecurityStateTokenValidator(UserSecurityStateService securityStateService) {
        this.securityStateService = securityStateService;
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt token) {
        String userId = token.getClaimAsString("user_id");
        if (userId == null) {
            return OAuth2TokenValidatorResult.success();
        }
        Number version = token.getClaim("security_version");
        try {
            return version != null
                    && securityStateService.isTokenStateCurrent(UUID.fromString(userId), version.intValue())
                            ? OAuth2TokenValidatorResult.success()
                            : OAuth2TokenValidatorResult.failure(INVALID_STATE);
        } catch (IllegalArgumentException exception) {
            return OAuth2TokenValidatorResult.failure(INVALID_STATE);
        }
    }
}

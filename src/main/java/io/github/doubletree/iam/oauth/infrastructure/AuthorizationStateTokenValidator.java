package io.github.doubletree.iam.oauth.infrastructure;

import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Enforces immediate revocation for tokens presented back to IdentityForge.
 * Independent resource servers still use bounded JWT lifetimes unless they add
 * their own introspection or revocation-state integration.
 */
public class AuthorizationStateTokenValidator implements OAuth2TokenValidator<Jwt> {

    private static final OAuth2Error REVOKED = new OAuth2Error(
            "invalid_token", "The token authorization is expired or revoked", null);
    private final AccessTokenAuthorizationState authorizationState;

    public AuthorizationStateTokenValidator(AccessTokenAuthorizationState authorizationState) {
        this.authorizationState = authorizationState;
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt token) {
        return authorizationState.isActive(token.getTokenValue())
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(REVOKED);
    }
}

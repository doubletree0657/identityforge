package io.github.doubletree.iam.oauth.infrastructure;

import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;

/**
 * Enforces immediate revocation for tokens presented back to IdentityForge.
 * Independent resource servers still use bounded JWT lifetimes unless they add
 * their own introspection or revocation-state integration.
 */
public class AuthorizationStateTokenValidator implements OAuth2TokenValidator<Jwt> {

    private static final OAuth2Error REVOKED = new OAuth2Error(
            "invalid_token", "The token authorization is expired or revoked", null);
    private final OAuth2AuthorizationService authorizationService;

    public AuthorizationStateTokenValidator(OAuth2AuthorizationService authorizationService) {
        this.authorizationService = authorizationService;
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt token) {
        OAuth2Authorization authorization = authorizationService.findByToken(
                token.getTokenValue(), OAuth2TokenType.ACCESS_TOKEN);
        return authorization != null
                        && authorization.getAccessToken() != null
                        && authorization.getAccessToken().isActive()
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(REVOKED);
    }
}

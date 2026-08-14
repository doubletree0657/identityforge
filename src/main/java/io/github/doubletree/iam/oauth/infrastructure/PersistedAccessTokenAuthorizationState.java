package io.github.doubletree.iam.oauth.infrastructure;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.stereotype.Component;

/**
 * Reads only token lifecycle columns for bearer-token validation. Rehydrating a
 * complete authorization also deserializes the browser principal and request
 * attributes, which are not needed to answer this security-state question.
 */
@Component
public class PersistedAccessTokenAuthorizationState implements AccessTokenAuthorizationState {

    private static final String ACTIVE_ACCESS_TOKEN_SQL = """
            select exists (
                select 1
                  from oauth2_authorization
                 where access_token_value = ?
                   and access_token_expires_at > current_timestamp
            )
            """;

    private final OAuth2AuthorizationService authorizationService;
    private final JdbcOperations jdbcOperations;

    public PersistedAccessTokenAuthorizationState(
            OAuth2AuthorizationService authorizationService,
            ObjectProvider<JdbcOperations> jdbcOperations) {
        this.authorizationService = authorizationService;
        this.jdbcOperations = jdbcOperations.getIfAvailable();
    }

    @Override
    public boolean isActive(String tokenValue) {
        if (tokenValue == null || tokenValue.isBlank()) {
            return false;
        }
        if (jdbcOperations != null) {
            return Boolean.TRUE.equals(jdbcOperations.queryForObject(
                    ACTIVE_ACCESS_TOKEN_SQL, Boolean.class, tokenValue));
        }
        var authorization = authorizationService.findByToken(tokenValue, OAuth2TokenType.ACCESS_TOKEN);
        return authorization != null
                && authorization.getAccessToken() != null
                && authorization.getAccessToken().isActive();
    }
}

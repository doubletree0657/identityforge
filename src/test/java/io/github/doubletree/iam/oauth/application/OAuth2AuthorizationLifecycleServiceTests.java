package io.github.doubletree.iam.oauth.application;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Timestamp;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;

class OAuth2AuthorizationLifecycleServiceTests {

    @Test
    void rotationHistoryStoresOnlyAHashOfTheUsedRefreshToken() {
        OAuth2AuthorizationService authorizationService = mock(OAuth2AuthorizationService.class);
        JdbcOperations jdbcOperations = mock(JdbcOperations.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<JdbcOperations> jdbcProvider = mock(ObjectProvider.class);
        when(jdbcProvider.getIfAvailable()).thenReturn(jdbcOperations);
        OAuth2AuthorizationLifecycleService lifecycleService =
                new OAuth2AuthorizationLifecycleService(authorizationService, jdbcProvider);
        OAuth2RefreshToken currentToken = new OAuth2RefreshToken(
                "current-refresh-token",
                Instant.now(),
                Instant.now().plusSeconds(600));
        RegisteredClient client = RegisteredClient.withId("client-id")
                .clientId("client")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("https://client.example.test/callback")
                .build();
        OAuth2Authorization authorization = OAuth2Authorization.withRegisteredClient(client)
                .id("authorization-id")
                .principalName("user")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .token(currentToken)
                .build();
        when(authorizationService.findByToken("current-refresh-token", OAuth2TokenType.REFRESH_TOKEN))
                .thenReturn(authorization);

        lifecycleService.recordRefreshTokenRotation("used-refresh-token", "current-refresh-token");

        verify(jdbcOperations).update(
                contains("insert into oauth2_refresh_token_history"),
                argThat(value -> value instanceof String hash
                        && hash.length() == 64
                        && !hash.contains("used-refresh-token")),
                eq("authorization-id"),
                any(Timestamp.class));
    }
}

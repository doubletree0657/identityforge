package io.github.doubletree.iam.oauth.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;

class PersistedAccessTokenAuthorizationStateTests {

    @Test
    void jdbcStateCheckDoesNotRehydrateTheStoredBrowserAuthorization() {
        OAuth2AuthorizationService authorizationService = mock(OAuth2AuthorizationService.class);
        JdbcOperations jdbcOperations = mock(JdbcOperations.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<JdbcOperations> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(jdbcOperations);
        when(jdbcOperations.queryForObject(anyString(), eq(Boolean.class), eq("active-token")))
                .thenReturn(true);

        var state = new PersistedAccessTokenAuthorizationState(authorizationService, provider);

        assertThat(state.isActive("active-token")).isTrue();
        verifyNoInteractions(authorizationService);
    }

    @Test
    void inMemoryFallbackStillRequiresAnActiveAccessToken() {
        OAuth2AuthorizationService authorizationService = mock(OAuth2AuthorizationService.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<JdbcOperations> provider = mock(ObjectProvider.class);
        OAuth2AccessToken accessToken = new OAuth2AccessToken(
                OAuth2AccessToken.TokenType.BEARER,
                "active-token",
                Instant.now().minusSeconds(10),
                Instant.now().plusSeconds(60));
        RegisteredClient client = RegisteredClient.withId("client-id")
                .clientId("client")
                .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                .build();
        OAuth2Authorization authorization = OAuth2Authorization.withRegisteredClient(client)
                .principalName("client")
                .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                .token(accessToken)
                .build();
        when(authorizationService.findByToken("active-token", OAuth2TokenType.ACCESS_TOKEN))
                .thenReturn(authorization);

        var state = new PersistedAccessTokenAuthorizationState(authorizationService, provider);

        assertThat(state.isActive("active-token")).isTrue();
        assertThat(state.isActive("missing-token")).isFalse();
    }
}

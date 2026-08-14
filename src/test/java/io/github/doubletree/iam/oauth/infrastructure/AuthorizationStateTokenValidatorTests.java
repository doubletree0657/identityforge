package io.github.doubletree.iam.oauth.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;

class AuthorizationStateTokenValidatorTests {

    private final OAuth2AuthorizationService authorizationService = mock(OAuth2AuthorizationService.class);
    private final AuthorizationStateTokenValidator validator =
            new AuthorizationStateTokenValidator(authorizationService);

    @Test
    void acceptsOnlyActivePersistedAccessTokens() {
        Jwt jwt = jwt("active-token");
        OAuth2AccessToken accessToken = new OAuth2AccessToken(
                OAuth2AccessToken.TokenType.BEARER,
                jwt.getTokenValue(),
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

        assertThat(validator.validate(jwt).hasErrors()).isFalse();
    }

    @Test
    void rejectsTokensWhoseAuthorizationWasRemoved() {
        Jwt jwt = jwt("revoked-token");

        assertThat(validator.validate(jwt).hasErrors()).isTrue();
    }

    private Jwt jwt(String tokenValue) {
        return new Jwt(
                tokenValue,
                Instant.now().minusSeconds(10),
                Instant.now().plusSeconds(60),
                Map.of("alg", "none"),
                Map.of("sub", "subject"));
    }
}

package io.github.doubletree.iam.oauth.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

class AuthorizationStateTokenValidatorTests {

    private final AccessTokenAuthorizationState authorizationState = mock(AccessTokenAuthorizationState.class);
    private final AuthorizationStateTokenValidator validator =
            new AuthorizationStateTokenValidator(authorizationState);

    @Test
    void acceptsOnlyActivePersistedAccessTokens() {
        Jwt jwt = jwt("active-token");
        when(authorizationState.isActive("active-token")).thenReturn(true);

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

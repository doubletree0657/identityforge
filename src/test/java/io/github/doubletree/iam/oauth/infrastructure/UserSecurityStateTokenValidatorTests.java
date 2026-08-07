package io.github.doubletree.iam.oauth.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.github.doubletree.iam.authentication.application.UserSecurityStateService;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

class UserSecurityStateTokenValidatorTests {

    private final UserSecurityStateService stateService = mock(UserSecurityStateService.class);
    private final UserSecurityStateTokenValidator validator = new UserSecurityStateTokenValidator(stateService);

    @Test
    void rejectsStaleUserSecurityState() {
        UUID userId = UUID.randomUUID();
        when(stateService.isTokenStateCurrent(userId, 4)).thenReturn(false);

        assertThat(validator.validate(userToken(userId, 4)).hasErrors()).isTrue();
    }

    @Test
    void acceptsCurrentUserStateAndLeavesClientTokensUnaffected() {
        UUID userId = UUID.randomUUID();
        when(stateService.isTokenStateCurrent(userId, 4)).thenReturn(true);

        assertThat(validator.validate(userToken(userId, 4)).hasErrors()).isFalse();
        assertThat(validator.validate(tokenWithoutUser()).hasErrors()).isFalse();
    }

    private Jwt userToken(UUID userId, int securityVersion) {
        return baseToken()
                .claim("user_id", userId.toString())
                .claim("security_version", securityVersion)
                .build();
    }

    private Jwt tokenWithoutUser() {
        return baseToken().build();
    }

    private Jwt.Builder baseToken() {
        return Jwt.withTokenValue("token")
                .header("alg", "none")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60));
    }
}

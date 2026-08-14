package io.github.doubletree.iam.oauth.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.doubletree.iam.authentication.infrastructure.PlatformUserDetails;
import io.github.doubletree.iam.directory.domain.AccountStatus;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.oidc.OidcScopes;

class OidcIdentityClaimsTests {

    @Test
    void optionalLegacyProfileFieldsAreOmittedInsteadOfBreakingJwtEncoding() {
        PlatformUserDetails user = new PlatformUserDetails(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "admin",
                null,
                "{noop}unused",
                AccountStatus.ACTIVE,
                Set.of("platform-admin"),
                Set.of("iam.admin"));

        var claims = new OidcIdentityClaims().idTokenClaims(user, Set.of(OidcScopes.PROFILE));

        assertThat(claims).containsKeys("sub", "tenant_id", "preferred_username", "account_status");
        assertThat(claims).doesNotContainKeys("name", "display_name", "tenant_name");
        assertThat(claims.values()).doesNotContainNull();
    }
}

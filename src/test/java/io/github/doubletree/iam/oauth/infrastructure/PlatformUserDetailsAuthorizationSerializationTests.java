package io.github.doubletree.iam.oauth.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.doubletree.iam.authentication.infrastructure.PlatformUserDetails;
import io.github.doubletree.iam.directory.domain.AccountStatus;
import java.security.Principal;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.jackson2.SecurityJackson2Modules;
import org.springframework.security.oauth2.server.authorization.jackson2.OAuth2AuthorizationServerJackson2Module;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

class PlatformUserDetailsAuthorizationSerializationTests {

    @Test
    void durableAuthorizationPrincipalRoundTripsWithoutPasswordHash() throws Exception {
        PlatformUserDetails user = new PlatformUserDetails(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "user",
                "User",
                "{bcrypt}sensitive-password-hash",
                AccountStatus.ACTIVE,
                Set.of("tenant-admin"),
                Set.of("iam.users.read"));
        var authentication = UsernamePasswordAuthenticationToken.authenticated(user, null, List.of());
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModules(SecurityJackson2Modules.getModules(getClass().getClassLoader()));
        mapper.registerModule(new OAuth2AuthorizationServerJackson2Module());

        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put(Principal.class.getName(), authentication);
        String json = mapper.writeValueAsString(attributes);
        Map<String, Object> restored = mapper.readValue(json, new TypeReference<>() {});
        var restoredAuthentication = (UsernamePasswordAuthenticationToken) restored.get(Principal.class.getName());
        var restoredUser = (PlatformUserDetails) restoredAuthentication.getPrincipal();

        assertThat(json).doesNotContain("sensitive-password-hash", "bcrypt");
        assertThat(restoredUser.userId()).isEqualTo(user.userId());
        assertThat(restoredUser.securityVersion()).isEqualTo(user.securityVersion());
        assertThat(restoredUser.getPassword()).isNull();
    }
}

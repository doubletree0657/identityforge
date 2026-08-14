package io.github.doubletree.iam.oauth.infrastructure;

import io.github.doubletree.iam.applications.domain.Client;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.settings.OAuth2TokenFormat;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;
import org.springframework.stereotype.Component;

@Component
public class RegisteredClientMapper {

    private final Duration accessTokenTimeToLive;
    private final Duration refreshTokenTimeToLive;
    private final Duration authorizationCodeTimeToLive;

    public RegisteredClientMapper(
            @Value("${iam.oauth.access-token-time-to-live:PT10M}") Duration accessTokenTimeToLive,
            @Value("${iam.oauth.refresh-token-time-to-live:PT8H}") Duration refreshTokenTimeToLive,
            @Value("${iam.oauth.authorization-code-time-to-live:PT5M}") Duration authorizationCodeTimeToLive) {
        this.accessTokenTimeToLive = accessTokenTimeToLive;
        this.refreshTokenTimeToLive = refreshTokenTimeToLive;
        this.authorizationCodeTimeToLive = authorizationCodeTimeToLive;
    }

    public RegisteredClient toRegisteredClient(Client client) {
        RegisteredClient.Builder builder = RegisteredClient.withId(client.getId().toString())
                .clientId(client.getClientId())
                .clientName(client.getClientName())
                .clientSecret(client.getClientSecretHash())
                .clientSettings(ClientSettings.builder()
                        .requireProofKey(client.isRequirePkce())
                        .requireAuthorizationConsent(client.isRequireConsent())
                        .build())
                .tokenSettings(TokenSettings.builder()
                        .accessTokenFormat(OAuth2TokenFormat.SELF_CONTAINED)
                        .accessTokenTimeToLive(accessTokenTimeToLive)
                        .refreshTokenTimeToLive(refreshTokenTimeToLive)
                        .authorizationCodeTimeToLive(authorizationCodeTimeToLive)
                        .reuseRefreshTokens(false)
                        .build());

        client.getAuthenticationMethods()
                .forEach(method -> builder.clientAuthenticationMethod(new ClientAuthenticationMethod(method)));
        client.getGrantTypes()
                .forEach(grantType -> builder.authorizationGrantType(new AuthorizationGrantType(grantType)));
        client.getRedirectUris().forEach(builder::redirectUri);
        client.getScopes().forEach(builder::scope);
        client.getAllowedResourcePermissions().stream()
                .filter(permission -> client.getResourceServer() != null
                        && permission.getResourceServer().getId().equals(client.getResourceServer().getId())
                        && permission.getResourceServer().getTenant().getId().equals(client.getTenant().getId()))
                .map(permission -> permission.getName())
                .forEach(builder::scope);

        return builder.build();
    }
}

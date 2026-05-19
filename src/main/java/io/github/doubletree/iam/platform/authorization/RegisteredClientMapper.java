package io.github.doubletree.iam.platform.authorization;

import io.github.doubletree.iam.platform.domain.Client;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.settings.OAuth2TokenFormat;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;
import org.springframework.stereotype.Component;

@Component
public class RegisteredClientMapper {

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
                        .build());

        client.getAuthenticationMethods()
                .forEach(method -> builder.clientAuthenticationMethod(new ClientAuthenticationMethod(method)));
        client.getGrantTypes()
                .forEach(grantType -> builder.authorizationGrantType(new AuthorizationGrantType(grantType)));
        client.getRedirectUris().forEach(builder::redirectUri);
        client.getScopes().forEach(builder::scope);

        return builder.build();
    }
}

package io.github.doubletree.iam.provisioning.web.dto;

import io.github.doubletree.iam.provisioning.api.ScimSchemas;
import java.util.List;

public record ScimServiceProviderConfigResponse(
        List<String> schemas,
        Supported patch,
        Bulk bulk,
        Filter filter,
        Supported changePassword,
        Supported sort,
        Supported etag,
        List<AuthenticationScheme> authenticationSchemes) {

    public static ScimServiceProviderConfigResponse supportedSubset() {
        return new ScimServiceProviderConfigResponse(
                List.of(ScimSchemas.SERVICE_PROVIDER_CONFIG),
                new Supported(true),
                new Bulk(false, 0, 0),
                new Filter(true, 100),
                new Supported(false),
                new Supported(false),
                new Supported(true),
                List.of(new AuthenticationScheme(
                        "oauthbearertoken",
                        "OAuth Bearer Token",
                        "OAuth 2.0 bearer token",
                        "https://www.rfc-editor.org/rfc/rfc6750",
                        true)));
    }

    public record Supported(boolean supported) {
    }

    public record Bulk(boolean supported, int maxOperations, int maxPayloadSize) {
    }

    public record Filter(boolean supported, int maxResults) {
    }

    public record AuthenticationScheme(
            String type,
            String name,
            String description,
            String specUri,
            boolean primary) {
    }
}

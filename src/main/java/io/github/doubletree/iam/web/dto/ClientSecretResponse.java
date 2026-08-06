package io.github.doubletree.iam.web.dto;

import io.github.doubletree.iam.application.result.ClientSecretResult;

public record ClientSecretResponse(ClientResponse client, String clientSecret) {

    public static ClientSecretResponse from(ClientSecretResult result) {
        return new ClientSecretResponse(ClientResponse.from(result.client()), result.clientSecret());
    }
}

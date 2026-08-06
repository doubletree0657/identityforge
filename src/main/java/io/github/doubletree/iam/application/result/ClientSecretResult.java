package io.github.doubletree.iam.application.result;

import io.github.doubletree.iam.domain.Client;

public record ClientSecretResult(Client client, String clientSecret) {
}

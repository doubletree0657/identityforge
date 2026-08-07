package io.github.doubletree.iam.applications.api;

import io.github.doubletree.iam.applications.domain.Client;

public record ClientSecretResult(Client client, String clientSecret) {
}

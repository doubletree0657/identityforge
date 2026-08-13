package io.github.doubletree.iam.provisioning.api;

import java.util.List;

public record ScimResultPage<T>(long totalResults, List<T> resources) {

    public ScimResultPage {
        resources = List.copyOf(resources);
    }
}

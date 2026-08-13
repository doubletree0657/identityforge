package io.github.doubletree.iam.provisioning.web.dto;

import java.time.Instant;

public record ScimMeta(
        String resourceType,
        Instant created,
        Instant lastModified,
        String version,
        String location) {
}

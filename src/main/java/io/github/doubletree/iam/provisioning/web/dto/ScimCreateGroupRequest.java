package io.github.doubletree.iam.provisioning.web.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.UUID;

public record ScimCreateGroupRequest(
        @NotBlank String displayName,
        List<UUID> members) {
}

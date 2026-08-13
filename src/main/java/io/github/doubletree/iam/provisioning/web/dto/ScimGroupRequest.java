package io.github.doubletree.iam.provisioning.web.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

public record ScimGroupRequest(
        List<String> schemas,
        @NotBlank String displayName,
        List<@Valid ScimMember> members) {

    public record ScimMember(@NotNull UUID value, String type) {
    }
}

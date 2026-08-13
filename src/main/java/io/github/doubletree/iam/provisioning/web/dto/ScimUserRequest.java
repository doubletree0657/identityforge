package io.github.doubletree.iam.provisioning.web.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;

public record ScimUserRequest(
        List<String> schemas,
        @NotBlank String userName,
        String displayName,
        Boolean active,
        List<@Valid ScimEmail> emails) {

    public record ScimEmail(@NotBlank String value, String type, Boolean primary) {
    }
}

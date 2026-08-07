package io.github.doubletree.iam.directory.web.dto;

import io.github.doubletree.iam.directory.domain.UserAttributeValueType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record SetUserAttributeRequest(
        @NotBlank @Size(max = 4000) String value,
        @NotNull UserAttributeValueType valueType) {
}

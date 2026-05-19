package io.github.doubletree.iam.platform.web.dto;

import io.github.doubletree.iam.platform.domain.UserAttributeValueType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record SetUserAttributeRequest(
        @NotBlank String value,
        @NotNull UserAttributeValueType valueType) {
}

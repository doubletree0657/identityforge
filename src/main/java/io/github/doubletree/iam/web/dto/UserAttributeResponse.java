package io.github.doubletree.iam.web.dto;

import io.github.doubletree.iam.domain.UserAttribute;
import io.github.doubletree.iam.domain.UserAttributeValueType;
import java.time.Instant;
import java.util.UUID;

public record UserAttributeResponse(
        UUID id,
        UUID userId,
        String name,
        String value,
        UserAttributeValueType valueType,
        Instant createdAt,
        Instant updatedAt) {

    public static UserAttributeResponse from(UserAttribute attribute) {
        return new UserAttributeResponse(
                attribute.getId(),
                attribute.getUser().getId(),
                attribute.getName(),
                attribute.getValue(),
                attribute.getValueType(),
                attribute.getCreatedAt(),
                attribute.getUpdatedAt());
    }
}

package io.github.doubletree.iam.web.dto;

import io.github.doubletree.iam.domain.Group;
import io.github.doubletree.iam.domain.User;
import java.util.List;
import java.util.UUID;

public record ScimUserResponse(
        List<String> schemas,
        UUID id,
        String userName,
        String displayName,
        boolean active,
        List<ScimGroupReference> groups) {

    private static final List<String> USER_SCHEMAS =
            List.of("urn:ietf:params:scim:schemas:core:2.0:User");

    public static ScimUserResponse from(User user) {
        return new ScimUserResponse(
                USER_SCHEMAS,
                user.getId(),
                user.getUsername(),
                user.getDisplayName(),
                true,
                user.getGroups().stream()
                        .map(ScimGroupReference::from)
                        .toList());
    }

    public record ScimGroupReference(UUID value, String display) {

        static ScimGroupReference from(Group group) {
            return new ScimGroupReference(group.getId(), group.getName());
        }
    }
}

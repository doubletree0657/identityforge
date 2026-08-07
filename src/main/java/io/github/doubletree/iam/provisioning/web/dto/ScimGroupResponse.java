package io.github.doubletree.iam.provisioning.web.dto;

import io.github.doubletree.iam.directory.domain.Group;
import io.github.doubletree.iam.directory.domain.User;
import java.util.List;
import java.util.UUID;

public record ScimGroupResponse(
        List<String> schemas,
        UUID id,
        String displayName,
        List<ScimMemberReference> members) {

    private static final List<String> GROUP_SCHEMAS =
            List.of("urn:ietf:params:scim:schemas:core:2.0:Group");

    public static ScimGroupResponse from(Group group) {
        return new ScimGroupResponse(
                GROUP_SCHEMAS,
                group.getId(),
                group.getName(),
                group.getUsers().stream()
                        .map(ScimMemberReference::from)
                        .toList());
    }

    public record ScimMemberReference(UUID value, String display) {

        static ScimMemberReference from(User user) {
            return new ScimMemberReference(user.getId(), user.getDisplayName());
        }
    }
}

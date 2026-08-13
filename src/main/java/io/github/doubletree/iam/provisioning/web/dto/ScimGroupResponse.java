package io.github.doubletree.iam.provisioning.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.github.doubletree.iam.directory.domain.Group;
import io.github.doubletree.iam.directory.domain.User;
import io.github.doubletree.iam.provisioning.api.ScimSchemas;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public record ScimGroupResponse(
        List<String> schemas,
        UUID id,
        String displayName,
        List<ScimMemberReference> members,
        ScimMeta meta) {

    public static ScimGroupResponse from(Group group, String locationPrefix) {
        return new ScimGroupResponse(
                ScimSchemas.GROUP_LIST,
                group.getId(),
                group.getDisplayName(),
                group.getUsers().stream()
                        .sorted(Comparator.comparing(User::getUsername, String.CASE_INSENSITIVE_ORDER))
                        .map(user -> ScimMemberReference.from(user, locationPrefix))
                        .toList(),
                new ScimMeta(
                        "Group",
                        group.getCreatedAt(),
                        group.getUpdatedAt(),
                        version(group.getVersion()),
                        locationPrefix + "/Groups/" + group.getId()));
    }

    private static String version(long version) {
        return "\"" + version + "\"";
    }

    public record ScimMemberReference(
            UUID value,
            @JsonProperty("$ref") String reference,
            String display,
            String type) {

        static ScimMemberReference from(User user, String locationPrefix) {
            return new ScimMemberReference(
                    user.getId(),
                    locationPrefix + "/Users/" + user.getId(),
                    user.getDisplayName(),
                    "User");
        }
    }
}

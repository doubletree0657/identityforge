package io.github.doubletree.iam.provisioning.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.github.doubletree.iam.directory.domain.AccountStatus;
import io.github.doubletree.iam.directory.domain.Group;
import io.github.doubletree.iam.directory.domain.User;
import io.github.doubletree.iam.provisioning.api.ScimSchemas;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public record ScimUserResponse(
        List<String> schemas,
        UUID id,
        String userName,
        String displayName,
        boolean active,
        List<ScimEmail> emails,
        List<ScimGroupReference> groups,
        ScimMeta meta) {

    public static ScimUserResponse from(User user, String locationPrefix) {
        return new ScimUserResponse(
                ScimSchemas.USER_LIST,
                user.getId(),
                user.getUsername(),
                user.getDisplayName(),
                user.getAccountStatus() == AccountStatus.ACTIVE,
                user.getEmail() == null
                        ? List.of()
                        : List.of(new ScimEmail(user.getEmail(), "work", true)),
                user.getGroups().stream()
                        .sorted(Comparator.comparing(Group::getDisplayName, String.CASE_INSENSITIVE_ORDER))
                        .map(group -> ScimGroupReference.from(group, locationPrefix))
                        .toList(),
                new ScimMeta(
                        "User",
                        user.getCreatedAt(),
                        user.getUpdatedAt(),
                        version(user.getVersion()),
                        locationPrefix + "/Users/" + user.getId()));
    }

    private static String version(long version) {
        return "\"" + version + "\"";
    }

    public record ScimEmail(String value, String type, boolean primary) {
    }

    public record ScimGroupReference(
            UUID value,
            @JsonProperty("$ref") String reference,
            String display,
            String type) {

        static ScimGroupReference from(Group group, String locationPrefix) {
            return new ScimGroupReference(
                    group.getId(),
                    locationPrefix + "/Groups/" + group.getId(),
                    group.getDisplayName(),
                    "direct");
        }
    }
}

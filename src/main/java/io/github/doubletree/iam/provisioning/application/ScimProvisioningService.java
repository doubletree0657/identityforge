package io.github.doubletree.iam.provisioning.application;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.doubletree.iam.audit.application.AuditApplicationService;
import io.github.doubletree.iam.directory.access.application.AdminAuthorizationService;
import io.github.doubletree.iam.directory.application.GroupApplicationService;
import io.github.doubletree.iam.directory.application.UserApplicationService;
import io.github.doubletree.iam.directory.domain.AccountStatus;
import io.github.doubletree.iam.directory.domain.Group;
import io.github.doubletree.iam.directory.domain.User;
import io.github.doubletree.iam.provisioning.api.ScimProtocolException;
import io.github.doubletree.iam.provisioning.api.ScimResultPage;
import io.github.doubletree.iam.provisioning.api.ScimSchemas;
import io.github.doubletree.iam.provisioning.web.dto.ScimGroupRequest;
import io.github.doubletree.iam.provisioning.web.dto.ScimPatchRequest;
import io.github.doubletree.iam.provisioning.web.dto.ScimUserRequest;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ScimProvisioningService {

    private final UserApplicationService userApplicationService;
    private final GroupApplicationService groupApplicationService;
    private final AuditApplicationService auditApplicationService;
    private final AdminAuthorizationService adminAuthorizationService;

    public ScimProvisioningService(
            UserApplicationService userApplicationService,
            GroupApplicationService groupApplicationService,
            AuditApplicationService auditApplicationService,
            AdminAuthorizationService adminAuthorizationService) {
        this.userApplicationService = userApplicationService;
        this.groupApplicationService = groupApplicationService;
        this.auditApplicationService = auditApplicationService;
        this.adminAuthorizationService = adminAuthorizationService;
    }

    @Transactional
    public User createUser(UUID tenantId, ScimUserRequest request) {
        validateResourceSchemas(request.schemas(), ScimSchemas.USER);
        String displayName = valueOrDefault(request.displayName(), request.userName());
        User created = userApplicationService.createUser(tenantId, request.userName(), displayName);
        User user = userApplicationService.replaceUser(
                tenantId,
                created.getId(),
                request.userName(),
                displayName,
                email(request.emails()),
                accountStatus(request.active(), true));
        auditApplicationService.recordEvent(tenantId, "SCIM_USER_CREATED", "USER", user.getId());
        return user;
    }

    @Transactional(readOnly = true)
    public User getUser(UUID tenantId, UUID userId) {
        return userApplicationService.findUser(tenantId, userId);
    }

    @Transactional(readOnly = true)
    public ScimResultPage<User> listUsers(
            UUID tenantId,
            String filterExpression,
            int startIndex,
            int count) {
        ScimPageRequest pageable = ScimPageRequest.of(startIndex, count);
        ScimFilter filter = ScimFilter.parse(filterExpression, ScimFilter.Resource.USER);
        Page<User> page;
        if (filter == null) {
            page = userApplicationService.listUsers(tenantId, pageable);
        } else {
            page = switch (filter.attribute()) {
                case "username" -> userApplicationService.listUsersByUsername(
                        tenantId, filter.stringValue(), pageable);
                case "displayname" -> userApplicationService.listUsersByDisplayName(
                        tenantId, filter.stringValue(), pageable);
                case "emails.value" -> userApplicationService.listUsersByEmail(
                        tenantId, filter.stringValue(), pageable);
                case "active" -> userApplicationService.listUsersByActive(
                        tenantId, filter.booleanValue(), pageable);
                default -> throw ScimProtocolException.invalidFilter("The user filter attribute is not supported");
            };
        }
        return new ScimResultPage<>(page.getTotalElements(), count == 0 ? List.of() : page.getContent());
    }

    @Transactional
    public User replaceUser(UUID tenantId, UUID userId, ScimUserRequest request) {
        return replaceUser(tenantId, userId, request, null);
    }

    @Transactional
    public User replaceUser(UUID tenantId, UUID userId, ScimUserRequest request, String ifMatch) {
        validateResourceSchemas(request.schemas(), ScimSchemas.USER);
        assertIfMatch(ifMatch, userApplicationService.findUser(tenantId, userId).getVersion());
        User user = userApplicationService.replaceUser(
                tenantId,
                userId,
                request.userName(),
                valueOrDefault(request.displayName(), request.userName()),
                email(request.emails()),
                accountStatus(request.active(), true));
        auditApplicationService.recordEvent(tenantId, "SCIM_USER_REPLACED", "USER", userId);
        return user;
    }

    @Transactional
    public User patchUser(UUID tenantId, UUID userId, ScimPatchRequest request) {
        return patchUser(tenantId, userId, request, null);
    }

    @Transactional
    public User patchUser(UUID tenantId, UUID userId, ScimPatchRequest request, String ifMatch) {
        validatePatchRequest(request);
        User current = userApplicationService.findUser(tenantId, userId);
        assertIfMatch(ifMatch, current.getVersion());
        String username = current.getUsername();
        String displayName = current.getDisplayName();
        String email = current.getEmail();
        boolean active = current.getAccountStatus() == AccountStatus.ACTIVE;

        for (ScimPatchRequest.Operation operation : request.operations()) {
            String op = operation.op().toLowerCase(Locale.ROOT);
            String path = normalizedPath(operation.path());
            if (!Set.of("add", "replace", "remove").contains(op)) {
                throw ScimProtocolException.invalidValue("Only add, replace, and remove PATCH operations are supported");
            }
            switch (path) {
                case "username" -> {
                    requireNotRemove(op, "userName");
                    username = requiredText(operation.value(), "userName");
                }
                case "displayname" -> {
                    requireNotRemove(op, "displayName");
                    displayName = requiredText(operation.value(), "displayName");
                }
                case "active" -> {
                    requireNotRemove(op, "active");
                    active = requiredBoolean(operation.value(), "active");
                }
                case "emails" -> email = "remove".equals(op) ? null : email(operation.value());
                default -> throw ScimProtocolException.invalidPath("The user PATCH path is not supported");
            }
        }
        User user = userApplicationService.replaceUser(
                tenantId,
                userId,
                username,
                displayName,
                email,
                active ? AccountStatus.ACTIVE : AccountStatus.DISABLED);
        auditApplicationService.recordEvent(tenantId, "SCIM_USER_PATCHED", "USER", userId);
        return user;
    }

    @Transactional
    public void deleteUser(UUID tenantId, UUID userId) {
        deleteUser(tenantId, userId, null);
    }

    @Transactional
    public void deleteUser(UUID tenantId, UUID userId, String ifMatch) {
        assertIfMatch(ifMatch, userApplicationService.findUser(tenantId, userId).getVersion());
        userApplicationService.deleteUser(tenantId, userId);
        auditApplicationService.recordEvent(tenantId, "SCIM_USER_DELETED", "USER", userId);
    }

    @Transactional
    public Group createGroup(UUID tenantId, ScimGroupRequest request) {
        validateResourceSchemas(request.schemas(), ScimSchemas.GROUP);
        List<UUID> members = memberIds(request.members()).stream().toList();
        Group group = groupApplicationService.createGroupWithMembers(tenantId, request.displayName(), members);
        auditApplicationService.recordEvent(tenantId, "SCIM_GROUP_CREATED", "GROUP", group.getId());
        if (!members.isEmpty()) {
            auditApplicationService.recordEvent(
                    tenantId, "SCIM_GROUP_MEMBERSHIP_CHANGED", "GROUP", group.getId());
        }
        return group;
    }

    @Transactional(readOnly = true)
    public Group getGroup(UUID tenantId, UUID groupId) {
        return groupApplicationService.findGroup(tenantId, groupId);
    }

    @Transactional(readOnly = true)
    public ScimResultPage<Group> listGroups(
            UUID tenantId,
            String filterExpression,
            int startIndex,
            int count) {
        ScimPageRequest pageable = ScimPageRequest.of(startIndex, count);
        ScimFilter filter = ScimFilter.parse(filterExpression, ScimFilter.Resource.GROUP);
        Page<Group> page;
        if (filter == null) {
            page = groupApplicationService.listGroups(tenantId, pageable);
        } else if ("displayname".equals(filter.attribute())) {
            page = groupApplicationService.listGroupsByDisplayName(tenantId, filter.stringValue(), pageable);
        } else if ("members.value".equals(filter.attribute())) {
            page = groupApplicationService.listGroupsByMember(
                    tenantId, parseUuid(filter.stringValue(), "members.value"), pageable);
        } else {
            throw ScimProtocolException.invalidFilter("The group filter attribute is not supported");
        }
        return new ScimResultPage<>(page.getTotalElements(), count == 0 ? List.of() : page.getContent());
    }

    @Transactional
    public Group replaceGroup(UUID tenantId, UUID groupId, ScimGroupRequest request) {
        return replaceGroup(tenantId, groupId, request, null);
    }

    @Transactional
    public Group replaceGroup(UUID tenantId, UUID groupId, ScimGroupRequest request, String ifMatch) {
        validateResourceSchemas(request.schemas(), ScimSchemas.GROUP);
        Group current = groupApplicationService.findGroup(tenantId, groupId);
        assertIfMatch(ifMatch, current.getVersion());
        Set<UUID> desiredMembers = memberIds(request.members());
        boolean membershipChanged = !memberIds(current).equals(desiredMembers);
        Group group = groupApplicationService.replaceGroup(
                tenantId, groupId, request.displayName(), desiredMembers);
        auditApplicationService.recordEvent(tenantId, "SCIM_GROUP_REPLACED", "GROUP", groupId);
        if (membershipChanged) {
            auditApplicationService.recordEvent(tenantId, "SCIM_GROUP_MEMBERSHIP_CHANGED", "GROUP", groupId);
        }
        return group;
    }

    @Transactional
    public Group patchGroup(UUID tenantId, UUID groupId, ScimPatchRequest request) {
        return patchGroup(tenantId, groupId, request, null);
    }

    @Transactional
    public Group patchGroup(UUID tenantId, UUID groupId, ScimPatchRequest request, String ifMatch) {
        validatePatchRequest(request);
        Group current = groupApplicationService.findGroup(tenantId, groupId);
        assertIfMatch(ifMatch, current.getVersion());
        String displayName = current.getDisplayName();
        Set<UUID> originalMembers = memberIds(current);
        Set<UUID> members = new LinkedHashSet<>(originalMembers);

        for (ScimPatchRequest.Operation operation : request.operations()) {
            String op = operation.op().toLowerCase(Locale.ROOT);
            String path = normalizedPath(operation.path());
            if (!Set.of("add", "replace", "remove").contains(op)) {
                throw ScimProtocolException.invalidValue("Only add, replace, and remove PATCH operations are supported");
            }
            if ("displayname".equals(path)) {
                requireNotRemove(op, "displayName");
                displayName = requiredText(operation.value(), "displayName");
                continue;
            }
            if ("members".equals(path)) {
                Set<UUID> values = "remove".equals(op) ? Set.of() : memberIds(operation.value());
                if ("add".equals(op)) {
                    members.addAll(values);
                } else {
                    members.clear();
                    members.addAll(values);
                }
                continue;
            }
            UUID removedMember = memberRemovalPath(path);
            if (removedMember != null && "remove".equals(op)) {
                members.remove(removedMember);
                continue;
            }
            throw ScimProtocolException.invalidPath("The group PATCH path is not supported");
        }
        Group group = groupApplicationService.replaceGroup(tenantId, groupId, displayName, members);
        auditApplicationService.recordEvent(tenantId, "SCIM_GROUP_PATCHED", "GROUP", groupId);
        if (!originalMembers.equals(members)) {
            auditApplicationService.recordEvent(tenantId, "SCIM_GROUP_MEMBERSHIP_CHANGED", "GROUP", groupId);
        }
        return group;
    }

    @Transactional
    public void deleteGroup(UUID tenantId, UUID groupId) {
        deleteGroup(tenantId, groupId, null);
    }

    @Transactional
    public void deleteGroup(UUID tenantId, UUID groupId, String ifMatch) {
        assertIfMatch(ifMatch, groupApplicationService.findGroup(tenantId, groupId).getVersion());
        groupApplicationService.deleteGroup(tenantId, groupId);
        auditApplicationService.recordEvent(tenantId, "SCIM_GROUP_DELETED", "GROUP", groupId);
    }

    public void assertTenantAccess(UUID tenantId) {
        adminAuthorizationService.assertTenantAccess(tenantId);
    }

    private void validateResourceSchemas(List<String> schemas, String expected) {
        if (schemas == null || schemas.size() != 1 || !expected.equals(schemas.getFirst())) {
            throw ScimProtocolException.invalidValue("The request must contain only the supported resource schema");
        }
    }

    private void validatePatchRequest(ScimPatchRequest request) {
        if (request.schemas().size() != 1 || !ScimSchemas.PATCH_OP.equals(request.schemas().getFirst())) {
            throw ScimProtocolException.invalidValue("The request must contain the SCIM PatchOp schema");
        }
    }

    private AccountStatus accountStatus(Boolean active, boolean defaultValue) {
        return Boolean.TRUE.equals(active == null ? defaultValue : active)
                ? AccountStatus.ACTIVE
                : AccountStatus.DISABLED;
    }

    private String email(List<ScimUserRequest.ScimEmail> emails) {
        if (emails == null || emails.isEmpty()) {
            return null;
        }
        if (emails.size() > 1) {
            throw ScimProtocolException.invalidValue("This SCIM subset supports one user email address");
        }
        return emails.getFirst().value();
    }

    private String email(JsonNode value) {
        if (value == null || value.isNull()) {
            throw ScimProtocolException.invalidValue("emails requires a value for add or replace");
        }
        JsonNode candidate = value.isArray() && value.size() == 1 ? value.get(0) : value;
        if (value.isArray() && value.size() != 1) {
            throw ScimProtocolException.invalidValue("This SCIM subset supports one user email address");
        }
        if (!candidate.isObject() || !candidate.hasNonNull("value") || !candidate.get("value").isTextual()) {
            throw ScimProtocolException.invalidValue("emails must contain one object with a string value");
        }
        return candidate.get("value").asText();
    }

    private Set<UUID> memberIds(List<ScimGroupRequest.ScimMember> members) {
        Set<UUID> result = new LinkedHashSet<>();
        if (members != null) {
            members.forEach(member -> {
                if (member.type() != null && !"User".equalsIgnoreCase(member.type())) {
                    throw ScimProtocolException.invalidValue("Nested groups are not supported");
                }
                result.add(member.value());
            });
        }
        return result;
    }

    private Set<UUID> memberIds(Group group) {
        return group.getUsers().stream()
                .map(User::getId)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    }

    private Set<UUID> memberIds(JsonNode value) {
        if (value == null || value.isNull()) {
            throw ScimProtocolException.invalidValue("members requires a value for add or replace");
        }
        List<JsonNode> values = new java.util.ArrayList<>();
        if (value.isArray()) {
            value.forEach(values::add);
        } else {
            values.add(value);
        }
        Set<UUID> result = new LinkedHashSet<>();
        for (JsonNode member : values) {
            if (!member.isObject() || !member.hasNonNull("value") || !member.get("value").isTextual()) {
                throw ScimProtocolException.invalidValue("Each member must contain a string value");
            }
            if (member.hasNonNull("type") && !"User".equalsIgnoreCase(member.get("type").asText())) {
                throw ScimProtocolException.invalidValue("Nested groups are not supported");
            }
            result.add(parseUuid(member.get("value").asText(), "members.value"));
        }
        return result;
    }

    private UUID memberRemovalPath(String path) {
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile(
                        "^members\\s*\\[\\s*value\\s+eq\\s+\"([0-9a-fA-F-]+)\"\\s*]$",
                        java.util.regex.Pattern.CASE_INSENSITIVE)
                .matcher(path);
        return matcher.matches() ? parseUuid(matcher.group(1), "members.value") : null;
    }

    private UUID parseUuid(String value, String attribute) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException exception) {
            throw ScimProtocolException.invalidValue(attribute + " must be a UUID");
        }
    }

    private String normalizedPath(String path) {
        if (path == null || path.isBlank()) {
            throw ScimProtocolException.invalidPath("A PATCH path is required by this supported subset");
        }
        return path.trim().toLowerCase(Locale.ROOT);
    }

    private void requireNotRemove(String operation, String path) {
        if ("remove".equals(operation)) {
            throw ScimProtocolException.invalidValue(path + " cannot be removed");
        }
    }

    private String requiredText(JsonNode value, String path) {
        if (value == null || !value.isTextual() || value.asText().isBlank()) {
            throw ScimProtocolException.invalidValue(path + " requires a non-empty string value");
        }
        return value.asText();
    }

    private boolean requiredBoolean(JsonNode value, String path) {
        if (value == null || !value.isBoolean()) {
            throw ScimProtocolException.invalidValue(path + " requires a boolean value");
        }
        return value.asBoolean();
    }

    private String valueOrDefault(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value;
    }

    private void assertIfMatch(String ifMatch, long currentVersion) {
        if (ifMatch == null || ifMatch.isBlank() || "*".equals(ifMatch.trim())) {
            return;
        }
        String current = "\"" + currentVersion + "\"";
        if (!current.equals(ifMatch.trim())) {
            throw ScimProtocolException.preconditionFailed("The resource version does not match If-Match");
        }
    }
}

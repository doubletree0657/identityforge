package io.github.doubletree.iam.provisioning.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.doubletree.iam.audit.application.AuditApplicationService;
import io.github.doubletree.iam.directory.access.application.AdminAuthorizationService;
import io.github.doubletree.iam.directory.application.GroupApplicationService;
import io.github.doubletree.iam.directory.application.UserApplicationService;
import io.github.doubletree.iam.directory.domain.AccountStatus;
import io.github.doubletree.iam.directory.domain.Group;
import io.github.doubletree.iam.directory.domain.Tenant;
import io.github.doubletree.iam.directory.domain.User;
import io.github.doubletree.iam.provisioning.api.ScimProtocolException;
import io.github.doubletree.iam.provisioning.api.ScimSchemas;
import io.github.doubletree.iam.provisioning.web.dto.ScimGroupRequest;
import io.github.doubletree.iam.provisioning.web.dto.ScimPatchRequest;
import io.github.doubletree.iam.provisioning.web.dto.ScimUserRequest;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;

class ScimProvisioningServiceTests {

    private static final UUID TENANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID SECOND_USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000003");
    private static final UUID GROUP_ID = UUID.fromString("00000000-0000-0000-0000-000000000004");

    private final UserApplicationService users = mock(UserApplicationService.class);
    private final GroupApplicationService groups = mock(GroupApplicationService.class);
    private final AuditApplicationService audit = mock(AuditApplicationService.class);
    private final AdminAuthorizationService authorization = mock(AdminAuthorizationService.class);
    private final ScimProvisioningService service = new ScimProvisioningService(users, groups, audit, authorization);
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void createsActiveUserWithOneEmailAndRecordsProtocolAudit() {
        User candidate = user(USER_ID, "alice", AccountStatus.PENDING);
        User active = user(USER_ID, "alice", AccountStatus.ACTIVE);
        when(users.createUser(TENANT_ID, "alice", "Alice Example")).thenReturn(candidate);
        when(users.replaceUser(TENANT_ID, USER_ID, "alice", "Alice Example", "alice@example.com", AccountStatus.ACTIVE))
                .thenReturn(active);

        User result = service.createUser(TENANT_ID, new ScimUserRequest(
                List.of(ScimSchemas.USER),
                "alice",
                "Alice Example",
                true,
                List.of(new ScimUserRequest.ScimEmail("alice@example.com", "work", true))));

        assertThat(result.getAccountStatus()).isEqualTo(AccountStatus.ACTIVE);
        verify(audit).recordEvent(TENANT_ID, "SCIM_USER_CREATED", "USER", USER_ID);
    }

    @Test
    void routesExactFiltersAndPreservesArbitraryStartIndex() {
        User user = user(USER_ID, "alice", AccountStatus.ACTIVE);
        when(users.listUsersByUsername(eq(TENANT_ID), eq("alice"), any()))
                .thenAnswer(invocation -> new PageImpl<>(List.of(user), invocation.getArgument(2), 20));

        var result = service.listUsers(TENANT_ID, "userName eq \"alice\"", 2, 10);

        assertThat(result.totalResults()).isEqualTo(20);
        ArgumentCaptor<org.springframework.data.domain.Pageable> pageable =
                ArgumentCaptor.forClass(org.springframework.data.domain.Pageable.class);
        verify(users).listUsersByUsername(eq(TENANT_ID), eq("alice"), pageable.capture());
        assertThat(pageable.getValue().getOffset()).isEqualTo(1);
    }

    @Test
    void patchesUserAttributesButRejectsReadOnlyOrUnsupportedPaths() throws Exception {
        User current = user(USER_ID, "alice", AccountStatus.ACTIVE);
        current.setDisplayName("Alice");
        when(users.findUser(TENANT_ID, USER_ID)).thenReturn(current);
        when(users.replaceUser(any(), any(), any(), any(), any(), any())).thenReturn(current);
        ScimPatchRequest request = patch("""
                {
                  "schemas":["urn:ietf:params:scim:api:messages:2.0:PatchOp"],
                  "Operations":[
                    {"op":"replace","path":"displayName","value":"Alice Example"},
                    {"op":"replace","path":"active","value":false}
                  ]
                }
                """);

        service.patchUser(TENANT_ID, USER_ID, request);

        verify(users).replaceUser(TENANT_ID, USER_ID, "alice", "Alice Example", null, AccountStatus.DISABLED);
        verify(audit).recordEvent(TENANT_ID, "SCIM_USER_PATCHED", "USER", USER_ID);

        ScimPatchRequest invalid = patch("""
                {"schemas":["urn:ietf:params:scim:api:messages:2.0:PatchOp"],
                 "Operations":[{"op":"replace","path":"groups","value":[]}]}
                """);
        assertThatThrownBy(() -> service.patchUser(TENANT_ID, USER_ID, invalid))
                .isInstanceOf(ScimProtocolException.class)
                .extracting(exception -> ((ScimProtocolException) exception).scimType())
                .isEqualTo("invalidPath");
    }

    @Test
    void patchesGroupMembershipWithAddAndFilteredRemove() throws Exception {
        Group current = group();
        current.addUser(user(USER_ID, "alice", AccountStatus.ACTIVE));
        when(groups.findGroup(TENANT_ID, GROUP_ID)).thenReturn(current);
        when(groups.replaceGroup(eq(TENANT_ID), eq(GROUP_ID), eq("engineering"), any())).thenReturn(current);
        ScimPatchRequest request = patch("""
                {
                  "schemas":["urn:ietf:params:scim:api:messages:2.0:PatchOp"],
                  "Operations":[
                    {"op":"add","path":"members","value":[{"value":"00000000-0000-0000-0000-000000000003"}]},
                    {"op":"remove","path":"members[value eq \\\"00000000-0000-0000-0000-000000000002\\\"]"}
                  ]
                }
                """);

        service.patchGroup(TENANT_ID, GROUP_ID, request);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Set<UUID>> members = ArgumentCaptor.forClass(Set.class);
        verify(groups).replaceGroup(eq(TENANT_ID), eq(GROUP_ID), eq("engineering"), members.capture());
        assertThat(members.getValue()).containsExactly(SECOND_USER_ID);
        verify(audit).recordEvent(TENANT_ID, "SCIM_GROUP_MEMBERSHIP_CHANGED", "GROUP", GROUP_ID);
    }

    @Test
    void rejectsUnsupportedSchemasAndNestedGroupMembers() {
        assertThatThrownBy(() -> service.createUser(TENANT_ID, new ScimUserRequest(
                List.of("urn:unsupported"), "alice", null, true, List.of())))
                .isInstanceOf(ScimProtocolException.class);
        assertThatThrownBy(() -> service.createGroup(TENANT_ID, new ScimGroupRequest(
                List.of(ScimSchemas.GROUP),
                "engineering",
                List.of(new ScimGroupRequest.ScimMember(GROUP_ID, "Group")))))
                .isInstanceOf(ScimProtocolException.class);
    }

    @Test
    void rejectsAStaleIfMatchVersionBeforeMutation() throws Exception {
        when(users.findUser(TENANT_ID, USER_ID)).thenReturn(user(USER_ID, "alice", AccountStatus.ACTIVE));
        ScimPatchRequest request = patch("""
                {"schemas":["urn:ietf:params:scim:api:messages:2.0:PatchOp"],
                 "Operations":[{"op":"replace","path":"active","value":false}]}
                """);

        assertThatThrownBy(() -> service.patchUser(TENANT_ID, USER_ID, request, "\"99\""))
                .isInstanceOf(ScimProtocolException.class)
                .extracting(exception -> ((ScimProtocolException) exception).status().value())
                .isEqualTo(412);
        org.mockito.Mockito.verifyNoInteractions(audit);
    }

    private ScimPatchRequest patch(String json) throws Exception {
        return objectMapper.readerFor(ScimPatchRequest.class).readValue(json);
    }

    private User user(UUID id, String username, AccountStatus status) {
        Tenant tenant = Tenant.create("SCIM Test Tenant");
        tenant.setId(TENANT_ID);
        User user = User.create(tenant, username, username);
        user.setId(id);
        user.setAccountStatus(status);
        return user;
    }

    private Group group() {
        Tenant tenant = Tenant.create("SCIM Test Tenant");
        tenant.setId(TENANT_ID);
        Group group = Group.create(tenant, "engineering");
        group.setId(GROUP_ID);
        return group;
    }
}

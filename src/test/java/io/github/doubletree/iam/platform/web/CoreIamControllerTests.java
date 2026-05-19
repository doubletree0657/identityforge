package io.github.doubletree.iam.platform.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.doubletree.iam.platform.application.exception.ClientValidationException;
import io.github.doubletree.iam.platform.application.exception.PasswordValidationException;
import io.github.doubletree.iam.platform.application.result.ClientSecretResult;
import io.github.doubletree.iam.platform.authorization.AuthorizationServerConfiguration;
import io.github.doubletree.iam.platform.application.service.ClientApplicationService;
import io.github.doubletree.iam.platform.application.exception.EntityNotFoundException;
import io.github.doubletree.iam.platform.application.service.GroupApplicationService;
import io.github.doubletree.iam.platform.application.service.PermissionApplicationService;
import io.github.doubletree.iam.platform.application.service.RoleApplicationService;
import io.github.doubletree.iam.platform.application.service.TenantApplicationService;
import io.github.doubletree.iam.platform.application.exception.TenantBoundaryViolationException;
import io.github.doubletree.iam.platform.application.service.UserApplicationService;
import io.github.doubletree.iam.platform.domain.Client;
import io.github.doubletree.iam.platform.domain.ClientStatus;
import io.github.doubletree.iam.platform.domain.ClientType;
import io.github.doubletree.iam.platform.domain.Group;
import io.github.doubletree.iam.platform.domain.PasswordCredential;
import io.github.doubletree.iam.platform.domain.Permission;
import io.github.doubletree.iam.platform.domain.Role;
import io.github.doubletree.iam.platform.domain.Tenant;
import io.github.doubletree.iam.platform.domain.User;
import io.github.doubletree.iam.platform.security.PasswordEncodingConfiguration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@WebMvcTest({
        TenantController.class,
        UserController.class,
        RoleController.class,
        PermissionController.class,
        ClientController.class,
        ScimController.class,
        RestExceptionHandler.class
})
@Import({AuthorizationServerConfiguration.class, PasswordEncodingConfiguration.class})
class CoreIamControllerTests {

    private static final UUID TENANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID ROLE_ID = UUID.fromString("00000000-0000-0000-0000-000000000003");
    private static final UUID PERMISSION_ID = UUID.fromString("00000000-0000-0000-0000-000000000004");
    private static final UUID CLIENT_ID = UUID.fromString("00000000-0000-0000-0000-000000000005");
    private static final UUID GROUP_ID = UUID.fromString("00000000-0000-0000-0000-000000000006");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TenantApplicationService tenantApplicationService;

    @MockitoBean
    private UserApplicationService userApplicationService;

    @MockitoBean
    private RoleApplicationService roleApplicationService;

    @MockitoBean
    private PermissionApplicationService permissionApplicationService;

    @MockitoBean
    private ClientApplicationService clientApplicationService;

    @MockitoBean
    private GroupApplicationService groupApplicationService;

    @MockitoBean
    private RegisteredClientRepository registeredClientRepository;

    private final RequestPostProcessor writeScopeJwt = jwt()
            .authorities(new SimpleGrantedAuthority("SCOPE_iam.write"));

    private final RequestPostProcessor readScopeJwt = jwt()
            .authorities(new SimpleGrantedAuthority("SCOPE_iam.read"));

    @Test
    void createsTenant() throws Exception {
        when(tenantApplicationService.createTenant(eq("Acme")))
                .thenReturn(tenant("Acme"));

        mockMvc.perform(post("/api/tenants")
                        .with(writeScopeJwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Acme"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(TENANT_ID.toString()))
                .andExpect(jsonPath("$.name").value("Acme"));
    }

    @Test
    void createsUserUnderTenant() throws Exception {
        when(userApplicationService.createUser(eq(TENANT_ID), eq("alice"), eq("Alice Example")))
                .thenReturn(user("alice", "Alice Example"));

        mockMvc.perform(post("/api/users")
                        .with(writeScopeJwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "tenantId":"00000000-0000-0000-0000-000000000001",
                                  "username":"alice",
                                  "displayName":"Alice Example"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(USER_ID.toString()))
                .andExpect(jsonPath("$.tenantId").value(TENANT_ID.toString()))
                .andExpect(jsonPath("$.username").value("alice"))
                .andExpect(jsonPath("$.displayName").value("Alice Example"))
                .andExpect(jsonPath("$.accountStatus").value("PENDING"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    void readsUserWithReadScope() throws Exception {
        when(userApplicationService.findUser(eq(USER_ID)))
                .thenReturn(user("read-user", "Read User"));

        mockMvc.perform(get("/api/users/{userId}", USER_ID)
                        .with(readScopeJwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(USER_ID.toString()))
                .andExpect(jsonPath("$.tenantId").value(TENANT_ID.toString()))
                .andExpect(jsonPath("$.username").value("read-user"))
                .andExpect(jsonPath("$.displayName").value("Read User"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    void createsRoleUnderTenant() throws Exception {
        when(roleApplicationService.createRole(eq(TENANT_ID), eq("admin")))
                .thenReturn(role("admin"));

        mockMvc.perform(post("/api/roles")
                        .with(writeScopeJwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "tenantId":"00000000-0000-0000-0000-000000000001",
                                  "name":"admin"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(ROLE_ID.toString()))
                .andExpect(jsonPath("$.tenantId").value(TENANT_ID.toString()))
                .andExpect(jsonPath("$.name").value("admin"));
    }

    @Test
    void createsPermission() throws Exception {
        when(permissionApplicationService.createPermission(eq(TENANT_ID), eq("clients:read")))
                .thenReturn(permission("clients:read"));

        mockMvc.perform(post("/api/permissions")
                        .with(writeScopeJwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "tenantId":"00000000-0000-0000-0000-000000000001",
                                  "name":"clients:read"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(PERMISSION_ID.toString()))
                .andExpect(jsonPath("$.tenantId").value(TENANT_ID.toString()))
                .andExpect(jsonPath("$.name").value("clients:read"));
    }

    @Test
    void assignsRoleToUser() throws Exception {
        User user = user("bob", "Bob Example");
        user.getRoles().add(role("operator"));
        when(userApplicationService.assignRoleToUser(eq(USER_ID), eq(ROLE_ID)))
                .thenReturn(user);

        mockMvc.perform(post("/api/users/{userId}/roles/{roleId}", USER_ID, ROLE_ID)
                        .with(writeScopeJwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(USER_ID.toString()))
                .andExpect(jsonPath("$.accountStatus").value("PENDING"))
                .andExpect(jsonPath("$.roleIds[0]").value(ROLE_ID.toString()))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    void updatesUserPassword() throws Exception {
        User user = user("password-user", "Password User");
        PasswordCredential credential = user.ensurePasswordCredential();
        credential.setPasswordHash("{bcrypt}sensitive-hash");
        when(userApplicationService.updatePassword(eq(USER_ID), eq("new-password-123")))
                .thenReturn(user);

        mockMvc.perform(put("/api/users/{userId}/password", USER_ID)
                        .with(writeScopeJwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "newPassword":"new-password-123",
                                  "passwordResetRequired":false
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(USER_ID.toString()))
                .andExpect(jsonPath("$.tenantId").value(TENANT_ID.toString()))
                .andExpect(jsonPath("$.username").value("password-user"))
                .andExpect(jsonPath("$.displayName").value("Password User"))
                .andExpect(jsonPath("$.accountStatus").value("PENDING"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.passwordUpdatedAt").doesNotExist())
                .andExpect(jsonPath("$.passwordResetRequired").doesNotExist())
                .andExpect(jsonPath("$.credentialsVersion").doesNotExist());
    }

    @Test
    void invalidPasswordUpdateReturnsBadRequest() throws Exception {
        when(userApplicationService.updatePassword(eq(USER_ID), eq("short")))
                .thenThrow(new PasswordValidationException("Password must be at least 8 characters"));

        mockMvc.perform(put("/api/users/{userId}/password", USER_ID)
                        .with(writeScopeJwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"newPassword":"short"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("password_validation_error"))
                .andExpect(jsonPath("$.message").value("Password must be at least 8 characters"));
    }

    @Test
    void passwordUpdateCanRequirePasswordReset() throws Exception {
        when(userApplicationService.updatePassword(eq(USER_ID), eq("temporary-password-123")))
                .thenReturn(user("temporary-user", "Temporary User"));
        when(userApplicationService.requirePasswordReset(eq(USER_ID)))
                .thenReturn(user("temporary-user", "Temporary User"));

        mockMvc.perform(put("/api/users/{userId}/password", USER_ID)
                        .with(writeScopeJwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "newPassword":"temporary-password-123",
                                  "passwordResetRequired":true
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());

        verify(userApplicationService).requirePasswordReset(eq(USER_ID));
    }

    @Test
    void assignsPermissionToRole() throws Exception {
        Role role = role("auditor");
        role.getPermissions().add(permission("users:read"));
        when(roleApplicationService.assignPermissionToRole(eq(ROLE_ID), eq(PERMISSION_ID)))
                .thenReturn(role);

        mockMvc.perform(post("/api/roles/{roleId}/permissions/{permissionId}", ROLE_ID, PERMISSION_ID)
                        .with(writeScopeJwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(ROLE_ID.toString()))
                .andExpect(jsonPath("$.permissionIds[0]").value(PERMISSION_ID.toString()));
    }

    @Test
    void createsClientUnderTenant() throws Exception {
        Client client = client("portal", "Portal");
        client.setClientSecretHash("{bcrypt}sensitive-client-secret-hash");
        when(clientApplicationService.createClientWithSecret(
                        eq(TENANT_ID),
                        eq("portal"),
                        eq("Portal"),
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                        any()))
                .thenReturn(new ClientSecretResult(client, "raw-client-secret-once"));

        mockMvc.perform(post("/api/clients")
                        .with(writeScopeJwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "tenantId":"00000000-0000-0000-0000-000000000001",
                                  "clientId":"portal",
                                  "name":"Portal"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.client.id").value(CLIENT_ID.toString()))
                .andExpect(jsonPath("$.client.tenantId").value(TENANT_ID.toString()))
                .andExpect(jsonPath("$.client.clientId").value("portal"))
                .andExpect(jsonPath("$.client.name").value("Portal"))
                .andExpect(jsonPath("$.client.clientSecretHash").doesNotExist())
                .andExpect(jsonPath("$.clientSecret").value("raw-client-secret-once"));
    }

    @Test
    void createsPublicClientThroughApiWithoutSecret() throws Exception {
        Client client = Client.create(tenant("Test Tenant"), "public-portal", "Public Portal");
        client.setClientType(ClientType.PUBLIC);
        client.setAuthenticationMethods(java.util.Set.of("none"));
        client.setId(CLIENT_ID);
        when(clientApplicationService.createClientWithSecret(
                        eq(TENANT_ID),
                        eq("public-portal"),
                        eq("Public Portal"),
                        eq(ClientType.PUBLIC),
                        eq(true),
                        eq(false),
                        any(),
                        any(),
                        any(),
                        any()))
                .thenReturn(new ClientSecretResult(client, null));

        mockMvc.perform(post("/api/clients")
                        .with(writeScopeJwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "tenantId":"00000000-0000-0000-0000-000000000001",
                                  "clientId":"public-portal",
                                  "name":"Public Portal",
                                  "clientType":"PUBLIC",
                                  "requirePkce":true,
                                  "requireConsent":false,
                                  "redirectUris":["https://public.example.test/callback"],
                                  "grantTypes":["authorization_code"],
                                  "scopes":["openid"],
                                  "authenticationMethods":["none"]
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.client.clientType").value("PUBLIC"))
                .andExpect(jsonPath("$.clientSecret").doesNotExist());
    }

    @Test
    void updatesClientThroughApi() throws Exception {
        Client client = client("portal", "Portal Updated");
        client.setStatus(ClientStatus.DISABLED);
        when(clientApplicationService.updateClient(
                        eq(CLIENT_ID),
                        eq("Portal Updated"),
                        eq(ClientStatus.DISABLED),
                        eq(true),
                        eq(false),
                        any(),
                        any(),
                        any(),
                        any()))
                .thenReturn(client);

        mockMvc.perform(put("/api/clients/{clientId}", CLIENT_ID)
                        .with(writeScopeJwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "clientName":"Portal Updated",
                                  "status":"DISABLED",
                                  "requirePkce":true,
                                  "requireConsent":false,
                                  "redirectUris":["https://portal.example.test/callback"],
                                  "grantTypes":["authorization_code"],
                                  "scopes":["iam.read"],
                                  "authenticationMethods":["client_secret_basic"]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(CLIENT_ID.toString()))
                .andExpect(jsonPath("$.name").value("Portal Updated"))
                .andExpect(jsonPath("$.status").value("DISABLED"))
                .andExpect(jsonPath("$.clientSecretHash").doesNotExist());
    }

    @Test
    void rotatesClientSecretThroughApi() throws Exception {
        Client client = client("portal", "Portal");
        client.setClientSecretHash("{bcrypt}rotated-secret-hash");
        when(clientApplicationService.rotateClientSecret(eq(CLIENT_ID)))
                .thenReturn(new ClientSecretResult(client, "rotated-client-secret-once"));

        mockMvc.perform(post("/api/clients/{clientId}/secret/rotation", CLIENT_ID)
                        .with(writeScopeJwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.client.id").value(CLIENT_ID.toString()))
                .andExpect(jsonPath("$.client.clientSecretHash").doesNotExist())
                .andExpect(jsonPath("$.clientSecret").value("rotated-client-secret-once"));
    }

    @Test
    void invalidClientConfigurationReturnsBadRequest() throws Exception {
        when(clientApplicationService.createClientWithSecret(
                        eq(TENANT_ID),
                        eq("public-secret-auth"),
                        eq("Public Secret Auth"),
                        eq(ClientType.PUBLIC),
                        eq(true),
                        any(),
                        any(),
                        any(),
                        any(),
                        any()))
                .thenThrow(new ClientValidationException("Public clients must not use client secret authentication"));

        mockMvc.perform(post("/api/clients")
                        .with(writeScopeJwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "tenantId":"00000000-0000-0000-0000-000000000001",
                                  "clientId":"public-secret-auth",
                                  "name":"Public Secret Auth",
                                  "clientType":"PUBLIC",
                                  "requirePkce":true,
                                  "authenticationMethods":["client_secret_basic"]
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("client_validation_error"))
                .andExpect(jsonPath("$.message").value("Public clients must not use client secret authentication"));
    }

    @Test
    void createsScimUser() throws Exception {
        when(userApplicationService.createUser(eq(TENANT_ID), eq("scim-user"), eq("SCIM User")))
                .thenReturn(user("scim-user", "SCIM User"));

        mockMvc.perform(post("/scim/v2/Users")
                        .with(writeScopeJwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "tenantId":"00000000-0000-0000-0000-000000000001",
                                  "userName":"scim-user",
                                  "displayName":"SCIM User"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.schemas[0]").value("urn:ietf:params:scim:schemas:core:2.0:User"))
                .andExpect(jsonPath("$.id").value(USER_ID.toString()))
                .andExpect(jsonPath("$.userName").value("scim-user"))
                .andExpect(jsonPath("$.displayName").value("SCIM User"))
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.mfaSecret").doesNotExist());
    }

    @Test
    void readsScimUser() throws Exception {
        User user = user("read-scim-user", "Read SCIM User");
        when(userApplicationService.findUser(eq(USER_ID)))
                .thenReturn(user);

        mockMvc.perform(get("/scim/v2/Users/{id}", USER_ID)
                        .with(readScopeJwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(USER_ID.toString()))
                .andExpect(jsonPath("$.userName").value("read-scim-user"))
                .andExpect(jsonPath("$.mfaSecret").doesNotExist());
    }

    @Test
    void createsScimGroup() throws Exception {
        Group group = group("engineering");
        group.addUser(user("scim-member", "SCIM Member"));
        when(groupApplicationService.createGroup(eq(TENANT_ID), eq("engineering")))
                .thenReturn(group("engineering"));
        when(groupApplicationService.addUserToGroup(eq(GROUP_ID), eq(USER_ID)))
                .thenReturn(group);

        mockMvc.perform(post("/scim/v2/Groups")
                        .with(writeScopeJwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "tenantId":"00000000-0000-0000-0000-000000000001",
                                  "displayName":"engineering",
                                  "members":["00000000-0000-0000-0000-000000000002"]
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.schemas[0]").value("urn:ietf:params:scim:schemas:core:2.0:Group"))
                .andExpect(jsonPath("$.id").value(GROUP_ID.toString()))
                .andExpect(jsonPath("$.displayName").value("engineering"))
                .andExpect(jsonPath("$.members[0].value").value(USER_ID.toString()));
    }

    @Test
    void readsScimGroup() throws Exception {
        Group group = group("readers");
        group.addUser(user("reader", "Reader User"));
        when(groupApplicationService.findGroup(eq(GROUP_ID)))
                .thenReturn(group);

        mockMvc.perform(get("/scim/v2/Groups/{id}", GROUP_ID)
                        .with(readScopeJwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(GROUP_ID.toString()))
                .andExpect(jsonPath("$.displayName").value("readers"))
                .andExpect(jsonPath("$.members[0].display").value("Reader User"));
    }

    @Test
    void unauthorizedScimRequestIsRejected() throws Exception {
        mockMvc.perform(post("/scim/v2/Users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "tenantId":"00000000-0000-0000-0000-000000000001",
                                  "userName":"scim-user",
                                  "displayName":"SCIM User"
                                }
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void entityNotFoundReturnsNotFound() throws Exception {
        when(userApplicationService.createUser(eq(TENANT_ID), eq("missing"), eq("Missing User")))
                .thenThrow(new EntityNotFoundException("Tenant not found: " + TENANT_ID));

        mockMvc.perform(post("/api/users")
                        .with(writeScopeJwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "tenantId":"00000000-0000-0000-0000-000000000001",
                                  "username":"missing",
                                  "displayName":"Missing User"
                                }
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("not_found"))
                .andExpect(jsonPath("$.message").value("Tenant not found: " + TENANT_ID));
    }

    @Test
    void crossTenantRoleAssignmentReturnsConflict() throws Exception {
        when(userApplicationService.assignRoleToUser(eq(USER_ID), eq(ROLE_ID)))
                .thenThrow(new TenantBoundaryViolationException("User and role must belong to the same tenant"));

        mockMvc.perform(post("/api/users/{userId}/roles/{roleId}", USER_ID, ROLE_ID)
                        .with(writeScopeJwt))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("tenant_boundary_violation"))
                .andExpect(jsonPath("$.message").value("User and role must belong to the same tenant"));
    }

    @Test
    void validationErrorReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/tenants")
                        .with(writeScopeJwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":""}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("validation_error"))
                .andExpect(jsonPath("$.message").value("Request validation failed"));
    }

    private Tenant tenant(String name) {
        Tenant tenant = Tenant.create(name);
        tenant.setId(TENANT_ID);
        return tenant;
    }

    private User user(String username, String displayName) {
        User user = User.create(tenant("Test Tenant"), username, displayName);
        user.setId(USER_ID);
        return user;
    }

    private Role role(String name) {
        Role role = Role.create(tenant("Test Tenant"), name);
        role.setId(ROLE_ID);
        return role;
    }

    private Permission permission(String name) {
        Permission permission = Permission.create(tenant("Test Tenant"), name);
        permission.setId(PERMISSION_ID);
        return permission;
    }

    private Client client(String clientId, String name) {
        Client client = Client.create(tenant("Test Tenant"), clientId, name);
        client.setId(CLIENT_ID);
        return client;
    }

    private Group group(String name) {
        Group group = Group.create(tenant("Test Tenant"), name);
        group.setId(GROUP_ID);
        return group;
    }
}

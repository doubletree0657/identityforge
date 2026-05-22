package io.github.doubletree.iam.platform.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.hamcrest.Matchers.containsString;

import io.github.doubletree.iam.platform.application.exception.ClientValidationException;
import io.github.doubletree.iam.platform.application.exception.PasswordValidationException;
import io.github.doubletree.iam.platform.application.exception.ValidationException;
import io.github.doubletree.iam.platform.application.result.ClientSecretResult;
import io.github.doubletree.iam.platform.application.result.MfaEnrollmentResult;
import io.github.doubletree.iam.platform.authorization.AuthorizationServerConfiguration;
import io.github.doubletree.iam.platform.application.service.AuditApplicationService;
import io.github.doubletree.iam.platform.application.service.ClientApplicationService;
import io.github.doubletree.iam.platform.application.exception.EntityNotFoundException;
import io.github.doubletree.iam.platform.application.service.GroupApplicationService;
import io.github.doubletree.iam.platform.application.service.MfaApplicationService;
import io.github.doubletree.iam.platform.application.service.PermissionApplicationService;
import io.github.doubletree.iam.platform.application.service.RoleApplicationService;
import io.github.doubletree.iam.platform.application.service.TenantApplicationService;
import io.github.doubletree.iam.platform.application.exception.TenantBoundaryViolationException;
import io.github.doubletree.iam.platform.application.service.UserApplicationService;
import io.github.doubletree.iam.platform.domain.AuditActorType;
import io.github.doubletree.iam.platform.domain.AuditLog;
import io.github.doubletree.iam.platform.domain.AuditResult;
import io.github.doubletree.iam.platform.domain.AccountStatus;
import io.github.doubletree.iam.platform.domain.Client;
import io.github.doubletree.iam.platform.domain.ClientStatus;
import io.github.doubletree.iam.platform.domain.ClientType;
import io.github.doubletree.iam.platform.domain.Group;
import io.github.doubletree.iam.platform.domain.PasswordCredential;
import io.github.doubletree.iam.platform.domain.Permission;
import io.github.doubletree.iam.platform.domain.Role;
import io.github.doubletree.iam.platform.domain.Tenant;
import io.github.doubletree.iam.platform.domain.TenantStatus;
import io.github.doubletree.iam.platform.domain.User;
import io.github.doubletree.iam.platform.domain.UserAttribute;
import io.github.doubletree.iam.platform.domain.UserAttributeValueType;
import io.github.doubletree.iam.platform.domain.UserProfile;
import io.github.doubletree.iam.platform.security.PasswordEncodingConfiguration;
import io.github.doubletree.iam.platform.security.authentication.MfaAuthenticationSuccessHandler;
import io.github.doubletree.iam.platform.security.authentication.PlatformUserDetails;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
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
        GroupController.class,
        MfaController.class,
        AuditLogController.class,
        CurrentUserController.class,
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
    private static final UUID ATTRIBUTE_ID = UUID.fromString("00000000-0000-0000-0000-000000000007");
    private static final UUID AUDIT_ID = UUID.fromString("00000000-0000-0000-0000-000000000008");

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
    private MfaApplicationService mfaApplicationService;

    @MockitoBean
    private AuditApplicationService auditApplicationService;

    @MockitoBean
    private RegisteredClientRepository registeredClientRepository;

    @MockitoBean
    private MfaAuthenticationSuccessHandler mfaAuthenticationSuccessHandler;

    private final RequestPostProcessor writeScopeJwt = jwt()
            .authorities(new SimpleGrantedAuthority("SCOPE_iam.write"));

    private final RequestPostProcessor readScopeJwt = jwt()
            .authorities(new SimpleGrantedAuthority("SCOPE_iam.read"));

    @Test
    void currentUserRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void currentUserReturnsSafePrincipalInformation() throws Exception {
        mockMvc.perform(get("/api/me")
                        .with(jwt().jwt(token -> token
                                        .subject("admin")
                                        .claim("user_id", USER_ID.toString())
                                        .claim("tenant_id", TENANT_ID.toString())
                                        .claim("display_name", "Development Super Admin")
                                        .claim("roles", List.of("platform-admin"))
                                        .claim("scope", "iam.read iam.write"))
                                .authorities(new SimpleGrantedAuthority("SCOPE_iam.read"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subject").value("admin"))
                .andExpect(jsonPath("$.username").value("admin"))
                .andExpect(jsonPath("$.userId").value(USER_ID.toString()))
                .andExpect(jsonPath("$.tenantId").value(TENANT_ID.toString()))
                .andExpect(jsonPath("$.displayName").value("Development Super Admin"))
                .andExpect(jsonPath("$.roles[0]").value("platform-admin"))
                .andExpect(jsonPath("$.scopes").isArray())
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.clientSecretHash").doesNotExist());
    }

    @Test
    void browserLogoutInvalidatesSessionAuditsAndRedirectsToFrontendLogin() throws Exception {
        PlatformUserDetails principal = new PlatformUserDetails(
                USER_ID,
                TENANT_ID,
                "admin",
                "Development Super Admin",
                "{noop}not-exposed",
                AccountStatus.ACTIVE,
                Set.of("platform-admin"),
                Set.of("iam.users.read"));

        mockMvc.perform(get("/logout").with(
                        org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
                                .user(principal)))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("http://localhost:5173/login?loggedOut=true"))
                .andExpect(header().string("Set-Cookie", containsString("JSESSIONID=;")))
                .andExpect(header().string("Set-Cookie", containsString("Max-Age=0")));

        verify(auditApplicationService).recordEvent(TENANT_ID, "USER_LOGGED_OUT", "USER", USER_ID);
    }

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
                .andExpect(jsonPath("$.name").value("Acme"))
                .andExpect(jsonPath("$.slug").value("acme"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void listsTenantsWithReadScope() throws Exception {
        when(tenantApplicationService.listTenants(any(Pageable.class)))
                .thenReturn(pageOf(tenant("Acme")));

        mockMvc.perform(get("/api/tenants")
                        .with(readScopeJwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(TENANT_ID.toString()))
                .andExpect(jsonPath("$.items[0].name").value("Acme"))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(50))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.totalPages").value(1))
                .andExpect(jsonPath("$.hasNext").value(false))
                .andExpect(jsonPath("$.hasPrevious").value(false));
    }

    @Test
    void rejectsOversizedPageRequests() throws Exception {
        mockMvc.perform(get("/api/tenants")
                        .queryParam("size", "101")
                        .with(readScopeJwt))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("validation_error"))
                .andExpect(jsonPath("$.message").value("Page size must be less than or equal to 100"));
    }

    @Test
    void updatesTenant() throws Exception {
        Tenant tenant = tenant("Acme Updated");
        tenant.setSlug("acme-updated");
        tenant.setStatus(TenantStatus.SUSPENDED);
        when(tenantApplicationService.updateTenant(eq(TENANT_ID), eq("Acme Updated"), eq("acme-updated"),
                eq(TenantStatus.SUSPENDED)))
                .thenReturn(tenant);

        mockMvc.perform(put("/api/tenants/{tenantId}", TENANT_ID)
                        .with(writeScopeJwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name":"Acme Updated",
                                  "slug":"acme-updated",
                                  "status":"SUSPENDED"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Acme Updated"))
                .andExpect(jsonPath("$.slug").value("acme-updated"))
                .andExpect(jsonPath("$.status").value("SUSPENDED"));
    }

    @Test
    void duplicateTenantSlugReturnsValidationError() throws Exception {
        when(tenantApplicationService.createTenant(eq("Acme")))
                .thenThrow(new ValidationException("Tenant slug already exists: acme"));

        mockMvc.perform(post("/api/tenants")
                        .with(writeScopeJwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Acme"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("validation_error"))
                .andExpect(jsonPath("$.message").value("Tenant slug already exists: acme"));
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
    void listsUsersFilteredByTenant() throws Exception {
        when(userApplicationService.listUsers(eq(TENANT_ID), any(Pageable.class)))
                .thenReturn(pageOf(user("alice", "Alice Example")));

        mockMvc.perform(get("/api/users")
                        .queryParam("tenantId", TENANT_ID.toString())
                        .with(readScopeJwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(USER_ID.toString()))
                .andExpect(jsonPath("$.items[0].passwordHash").doesNotExist())
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void updatesUserIdentityFields() throws Exception {
        User user = user("alice", "Alice Updated");
        user.setEmail("alice@example.test");
        user.setPhoneNumber("+15551234567");
        when(userApplicationService.updateUser(
                        eq(USER_ID),
                        eq("Alice Updated"),
                        eq("alice@example.test"),
                        eq(true),
                        eq("+15551234567"),
                        eq(false),
                        any()))
                .thenReturn(user);

        mockMvc.perform(put("/api/users/{userId}", USER_ID)
                        .with(writeScopeJwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "displayName":"Alice Updated",
                                  "email":"alice@example.test",
                                  "emailVerified":true,
                                  "phoneNumber":"+15551234567",
                                  "phoneNumberVerified":false,
                                  "accountStatus":"ACTIVE"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Alice Updated"))
                .andExpect(jsonPath("$.email").value("alice@example.test"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    void invalidUpdateUserInputReturnsBadRequest() throws Exception {
        mockMvc.perform(put("/api/users/{userId}", USER_ID)
                        .with(writeScopeJwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"not-an-email"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("validation_error"));
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
    void managesUserProfile() throws Exception {
        when(userApplicationService.updateProfile(
                        eq(USER_ID),
                        eq("Alice"),
                        eq("Example"),
                        eq("Al"),
                        eq("en-US"),
                        eq("America/Los_Angeles"),
                        eq("https://cdn.example.test/alice.png"),
                        eq("Engineer"),
                        eq("Platform"),
                        eq("Acme"),
                        eq("E123")))
                .thenReturn(profile(user("alice", "Alice Example")));

        mockMvc.perform(put("/api/users/{userId}/profile", USER_ID)
                        .with(writeScopeJwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "givenName":"Alice",
                                  "familyName":"Example",
                                  "preferredName":"Al",
                                  "locale":"en-US",
                                  "timezone":"America/Los_Angeles",
                                  "avatarUrl":"https://cdn.example.test/alice.png",
                                  "jobTitle":"Engineer",
                                  "department":"Platform",
                                  "organization":"Acme",
                                  "employeeNumber":"E123"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(USER_ID.toString()))
                .andExpect(jsonPath("$.givenName").value("Alice"))
                .andExpect(jsonPath("$.employeeNumber").value("E123"))
                .andExpect(jsonPath("$.totpSecret").doesNotExist());
    }

    @Test
    void readsDefaultProfileWhenUserHasNoProfileYet() throws Exception {
        when(userApplicationService.findProfileByUserId(eq(USER_ID)))
                .thenReturn(UserProfile.create(user("empty-profile-user", "Empty Profile User")));

        mockMvc.perform(get("/api/users/{userId}/profile", USER_ID)
                        .with(readScopeJwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(USER_ID.toString()))
                .andExpect(jsonPath("$.givenName").doesNotExist())
                .andExpect(jsonPath("$.totpSecret").doesNotExist());
    }

    @Test
    void missingUserProfileForMissingUserReturnsNotFound() throws Exception {
        when(userApplicationService.findProfileByUserId(eq(USER_ID)))
                .thenThrow(new EntityNotFoundException("User not found: " + USER_ID));

        mockMvc.perform(get("/api/users/{userId}/profile", USER_ID)
                        .with(readScopeJwt))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("not_found"));
    }

    @Test
    void managesUserAttributesWithoutSecrets() throws Exception {
        when(userApplicationService.setAttribute(eq(USER_ID), eq("costCenter"), eq("PLATFORM"),
                eq(UserAttributeValueType.STRING)))
                .thenReturn(attribute(user("alice", "Alice Example"), "costCenter", "PLATFORM"));

        mockMvc.perform(put("/api/users/{userId}/attributes/{name}", USER_ID, "costCenter")
                        .with(writeScopeJwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "value":"PLATFORM",
                                  "valueType":"STRING"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("costCenter"))
                .andExpect(jsonPath("$.value").value("PLATFORM"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.totpSecret").doesNotExist());
    }

    @Test
    void secretLikeUserAttributeNamesReturnGenericValidationError() throws Exception {
        when(userApplicationService.setAttribute(eq(USER_ID), eq("apiSecret"), eq("sensitive"),
                eq(UserAttributeValueType.STRING)))
                .thenThrow(new ValidationException("Secret-like values must not be stored as user attributes"));

        mockMvc.perform(put("/api/users/{userId}/attributes/{name}", USER_ID, "apiSecret")
                        .with(writeScopeJwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "value":"sensitive",
                                  "valueType":"STRING"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("validation_error"))
                .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.not("password_validation_error")));
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
    void listsRolesAndPermissions() throws Exception {
        when(roleApplicationService.listRoles(eq(TENANT_ID), any(Pageable.class)))
                .thenReturn(pageOf(role("admin")));
        when(permissionApplicationService.listPermissions(eq(TENANT_ID), any(Pageable.class)))
                .thenReturn(pageOf(permission("users:read")));

        mockMvc.perform(get("/api/roles").queryParam("tenantId", TENANT_ID.toString()).with(readScopeJwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(ROLE_ID.toString()))
                .andExpect(jsonPath("$.totalElements").value(1));
        mockMvc.perform(get("/api/permissions").queryParam("tenantId", TENANT_ID.toString()).with(readScopeJwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(PERMISSION_ID.toString()))
                .andExpect(jsonPath("$.totalElements").value(1));
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
    void listsAndReadsClientsSafely() throws Exception {
        Client client = client("portal", "Portal");
        client.setClientSecretHash("{bcrypt}sensitive-client-secret-hash");
        when(clientApplicationService.listClients(eq(TENANT_ID), any(Pageable.class)))
                .thenReturn(pageOf(client));
        when(clientApplicationService.findClient(eq(CLIENT_ID)))
                .thenReturn(client);

        mockMvc.perform(get("/api/clients").queryParam("tenantId", TENANT_ID.toString()).with(readScopeJwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(CLIENT_ID.toString()))
                .andExpect(jsonPath("$.items[0].clientSecretHash").doesNotExist())
                .andExpect(jsonPath("$.items[0].clientSecret").doesNotExist())
                .andExpect(jsonPath("$.totalElements").value(1));
        mockMvc.perform(get("/api/clients/{clientId}", CLIENT_ID).with(readScopeJwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(CLIENT_ID.toString()))
                .andExpect(jsonPath("$.clientSecretHash").doesNotExist());
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
    void managesGroupsAndMembers() throws Exception {
        Group group = group("engineering");
        group.addUser(user("member", "Member User"));
        when(groupApplicationService.createGroup(eq(TENANT_ID), eq("engineering")))
                .thenReturn(group("engineering"));
        when(groupApplicationService.updateGroup(eq(GROUP_ID), any(), eq("Engineering"), eq("Platform team")))
                .thenReturn(group("engineering"));
        when(groupApplicationService.addUserToGroup(eq(GROUP_ID), eq(USER_ID)))
                .thenReturn(group);
        when(groupApplicationService.findGroup(eq(GROUP_ID)))
                .thenReturn(group);
        when(groupApplicationService.listGroups(eq(TENANT_ID), any(Pageable.class)))
                .thenReturn(pageOf(group));

        mockMvc.perform(post("/api/groups")
                        .with(writeScopeJwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "tenantId":"00000000-0000-0000-0000-000000000001",
                                  "name":"engineering",
                                  "displayName":"Engineering",
                                  "description":"Platform team"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(GROUP_ID.toString()))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
        mockMvc.perform(post("/api/groups/{groupId}/members/{userId}", GROUP_ID, USER_ID).with(writeScopeJwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.memberIds[0]").value(USER_ID.toString()));
        mockMvc.perform(get("/api/groups").queryParam("tenantId", TENANT_ID.toString()).with(readScopeJwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(GROUP_ID.toString()))
                .andExpect(jsonPath("$.totalElements").value(1));
        mockMvc.perform(get("/api/groups/{groupId}/members", GROUP_ID).with(readScopeJwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(USER_ID.toString()));
    }

    @Test
    void mfaEnrollmentOnlyReturnsSecretOnce() throws Exception {
        when(mfaApplicationService.enrollTotp(eq(USER_ID)))
                .thenReturn(new MfaEnrollmentResult(USER_ID, "BASE32SECRET"));
        when(mfaApplicationService.verifyTotp(eq(USER_ID), eq("123456")))
                .thenReturn(true);

        mockMvc.perform(post("/api/users/{userId}/mfa/totp/enrollment", USER_ID).with(writeScopeJwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(USER_ID.toString()))
                .andExpect(jsonPath("$.secret").value("BASE32SECRET"))
                .andExpect(jsonPath("$.secretCiphertext").doesNotExist());
        mockMvc.perform(post("/api/users/{userId}/mfa/totp/verification", USER_ID)
                        .with(writeScopeJwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"123456"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verified").value(true))
                .andExpect(jsonPath("$.secret").doesNotExist())
                .andExpect(jsonPath("$.secretCiphertext").doesNotExist());
    }

    @Test
    void queriesAuditLogsWithoutSecretFields() throws Exception {
        when(auditApplicationService.listAuditLogs(eq(TENANT_ID), eq("USER_PASSWORD_SET"), eq("USER"),
                eq(USER_ID), eq(AuditResult.SUCCESS), any(Pageable.class)))
                .thenReturn(pageOf(auditLog()));

        mockMvc.perform(get("/api/audit-logs")
                        .queryParam("tenantId", TENANT_ID.toString())
                        .queryParam("action", "USER_PASSWORD_SET")
                        .queryParam("resourceType", "USER")
                        .queryParam("resourceId", USER_ID.toString())
                        .queryParam("result", "SUCCESS")
                        .with(readScopeJwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(AUDIT_ID.toString()))
                .andExpect(jsonPath("$.items[0].action").value("USER_PASSWORD_SET"))
                .andExpect(jsonPath("$.items[0].password").doesNotExist())
                .andExpect(jsonPath("$.items[0].clientSecret").doesNotExist())
                .andExpect(jsonPath("$.items[0].totpSecret").doesNotExist())
                .andExpect(jsonPath("$.items[0].accessToken").doesNotExist())
                .andExpect(jsonPath("$.totalElements").value(1));
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
    void writeScopeIsRequiredForDeleteApiOperations() throws Exception {
        mockMvc.perform(delete("/api/users/{userId}/attributes/{name}", USER_ID, "costCenter")
                        .with(readScopeJwt))
                .andExpect(status().isForbidden());
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

    private <T> PageImpl<T> pageOf(T item) {
        return new PageImpl<>(List.of(item), PageRequest.of(0, 50), 1);
    }

    private UserProfile profile(User user) {
        UserProfile profile = UserProfile.create(user);
        profile.setId(UUID.fromString("00000000-0000-0000-0000-000000000009"));
        profile.setGivenName("Alice");
        profile.setFamilyName("Example");
        profile.setPreferredName("Al");
        profile.setLocale("en-US");
        profile.setTimezone("America/Los_Angeles");
        profile.setAvatarUrl("https://cdn.example.test/alice.png");
        profile.setJobTitle("Engineer");
        profile.setDepartment("Platform");
        profile.setOrganization("Acme");
        profile.setEmployeeNumber("E123");
        return profile;
    }

    private UserAttribute attribute(User user, String name, String value) {
        UserAttribute attribute = UserAttribute.create(user, name, value, UserAttributeValueType.STRING);
        attribute.setId(ATTRIBUTE_ID);
        return attribute;
    }

    private AuditLog auditLog() {
        AuditLog auditLog = AuditLog.record(
                TENANT_ID,
                AuditActorType.API_CLIENT,
                null,
                "USER_PASSWORD_SET",
                "USER",
                USER_ID,
                AuditResult.SUCCESS);
        auditLog.setId(AUDIT_ID);
        return auditLog;
    }
}

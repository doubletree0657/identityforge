package io.github.doubletree.iam.web;

import io.github.doubletree.iam.applications.web.ClientController;
import io.github.doubletree.iam.applications.web.ResourceServerController;
import io.github.doubletree.iam.audit.web.AuditLogController;
import io.github.doubletree.iam.authentication.web.MfaController;
import io.github.doubletree.iam.directory.web.GroupController;
import io.github.doubletree.iam.directory.web.PermissionController;
import io.github.doubletree.iam.directory.web.RoleController;
import io.github.doubletree.iam.directory.web.TenantController;
import io.github.doubletree.iam.directory.web.UserController;
import io.github.doubletree.iam.oauth.web.CurrentUserController;
import io.github.doubletree.iam.oauth.web.AuthPageController;
import io.github.doubletree.iam.provisioning.web.ScimController;
import io.github.doubletree.iam.provisioning.web.ScimExceptionHandler;
import io.github.doubletree.iam.provisioning.application.ScimProvisioningService;
import io.github.doubletree.iam.provisioning.api.ScimProtocolException;
import io.github.doubletree.iam.provisioning.api.ScimResultPage;
import io.github.doubletree.iam.shared.web.RestExceptionHandler;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.hamcrest.Matchers.containsString;

import io.github.doubletree.iam.shared.exception.ClientValidationException;
import io.github.doubletree.iam.shared.exception.PasswordValidationException;
import io.github.doubletree.iam.shared.exception.ValidationException;
import io.github.doubletree.iam.applications.api.ClientSecretResult;
import io.github.doubletree.iam.authentication.api.MfaEnrollmentResult;
import io.github.doubletree.iam.authentication.api.MfaRecoveryCodesResult;
import io.github.doubletree.iam.authentication.api.MfaStatus;
import io.github.doubletree.iam.authentication.api.MfaVerificationResult;
import io.github.doubletree.iam.oauth.infrastructure.AuthorizationServerConfiguration;
import io.github.doubletree.iam.oauth.infrastructure.AccessTokenAuthorizationState;
import io.github.doubletree.iam.oauth.infrastructure.FileSigningKeyProvider;
import io.github.doubletree.iam.oauth.application.OAuth2AuthorizationLifecycleService;
import io.github.doubletree.iam.authentication.application.UserSecurityStateService;
import io.github.doubletree.iam.audit.application.AuditApplicationService;
import io.github.doubletree.iam.applications.application.ClientApplicationService;
import io.github.doubletree.iam.shared.exception.EntityNotFoundException;
import io.github.doubletree.iam.directory.application.GroupApplicationService;
import io.github.doubletree.iam.authentication.application.MfaApplicationService;
import io.github.doubletree.iam.directory.application.PermissionApplicationService;
import io.github.doubletree.iam.applications.application.ResourceServerApplicationService;
import io.github.doubletree.iam.directory.application.RoleApplicationService;
import io.github.doubletree.iam.directory.application.TenantApplicationService;
import io.github.doubletree.iam.shared.exception.TenantBoundaryViolationException;
import io.github.doubletree.iam.directory.application.UserApplicationService;
import io.github.doubletree.iam.audit.domain.AuditActorType;
import io.github.doubletree.iam.audit.domain.AuditLog;
import io.github.doubletree.iam.audit.domain.AuditResult;
import io.github.doubletree.iam.directory.domain.AccountStatus;
import io.github.doubletree.iam.applications.domain.Client;
import io.github.doubletree.iam.applications.domain.ClientStatus;
import io.github.doubletree.iam.applications.domain.ClientType;
import io.github.doubletree.iam.directory.domain.Group;
import io.github.doubletree.iam.directory.domain.PasswordCredential;
import io.github.doubletree.iam.directory.domain.Permission;
import io.github.doubletree.iam.applications.domain.ResourcePermission;
import io.github.doubletree.iam.applications.domain.ResourceServer;
import io.github.doubletree.iam.applications.domain.ResourceServerStatus;
import io.github.doubletree.iam.directory.domain.Role;
import io.github.doubletree.iam.directory.domain.Tenant;
import io.github.doubletree.iam.directory.domain.TenantStatus;
import io.github.doubletree.iam.directory.domain.User;
import io.github.doubletree.iam.directory.domain.UserAttribute;
import io.github.doubletree.iam.directory.domain.UserAttributeValueType;
import io.github.doubletree.iam.directory.domain.UserProfile;
import io.github.doubletree.iam.authentication.infrastructure.PasswordEncodingConfiguration;
import io.github.doubletree.iam.authentication.infrastructure.MfaAuthenticationSuccessHandler;
import io.github.doubletree.iam.authentication.infrastructure.PlatformUserDetails;
import io.github.doubletree.iam.authentication.infrastructure.SecurityContextCurrentActor;
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
import org.springframework.http.HttpHeaders;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@WebMvcTest({
        TenantController.class,
        UserController.class,
        RoleController.class,
        PermissionController.class,
        ClientController.class,
        ResourceServerController.class,
        GroupController.class,
        MfaController.class,
        AuditLogController.class,
        CurrentUserController.class,
        AuthPageController.class,
        ScimController.class,
        ScimExceptionHandler.class,
        RestExceptionHandler.class
})
@Import({
        AuthorizationServerConfiguration.class,
        FileSigningKeyProvider.class,
        PasswordEncodingConfiguration.class,
        SecurityContextCurrentActor.class
})
class CoreIamControllerTests {

    private static final UUID TENANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID OTHER_TENANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000009");
    private static final UUID ROLE_ID = UUID.fromString("00000000-0000-0000-0000-000000000003");
    private static final UUID PERMISSION_ID = UUID.fromString("00000000-0000-0000-0000-000000000004");
    private static final UUID CLIENT_ID = UUID.fromString("00000000-0000-0000-0000-000000000005");
    private static final UUID GROUP_ID = UUID.fromString("00000000-0000-0000-0000-000000000006");
    private static final UUID ATTRIBUTE_ID = UUID.fromString("00000000-0000-0000-0000-000000000007");
    private static final UUID AUDIT_ID = UUID.fromString("00000000-0000-0000-0000-000000000008");
    private static final UUID RESOURCE_SERVER_ID = UUID.fromString("00000000-0000-0000-0000-000000000010");
    private static final UUID RESOURCE_PERMISSION_ID = UUID.fromString("00000000-0000-0000-0000-000000000011");

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
    private ResourceServerApplicationService resourceServerApplicationService;

    @MockitoBean
    private GroupApplicationService groupApplicationService;

    @MockitoBean
    private ScimProvisioningService scimProvisioningService;

    @MockitoBean
    private MfaApplicationService mfaApplicationService;

    @MockitoBean
    private AuditApplicationService auditApplicationService;

    @MockitoBean
    private RegisteredClientRepository registeredClientRepository;

    @MockitoBean
    private MfaAuthenticationSuccessHandler mfaAuthenticationSuccessHandler;

    @MockitoBean
    private UserSecurityStateService userSecurityStateService;

    @MockitoBean
    private OAuth2AuthorizationLifecycleService authorizationLifecycleService;

    @MockitoBean
    private AccessTokenAuthorizationState accessTokenAuthorizationState;

    private final RequestPostProcessor writeScopeJwt = jwt()
            .jwt(token -> token
                    .claim("tenant_id", TENANT_ID.toString())
                    .claim("effective_roles", List.of("platform-admin"))
                    .claim("effective_permissions", List.of("iam.admin"))
                    .claim("aud", List.of("identityforge-admin-api"))
                    .claim("scope", "iam.read iam.write"))
            .authorities(new SimpleGrantedAuthority("SCOPE_iam.write"));

    private final RequestPostProcessor readScopeJwt = jwt()
            .jwt(token -> token
                    .claim("tenant_id", TENANT_ID.toString())
                    .claim("effective_roles", List.of("platform-admin"))
                    .claim("effective_permissions", List.of("iam.admin"))
                    .claim("aud", List.of("identityforge-admin-api"))
                    .claim("scope", "iam.read"))
            .authorities(new SimpleGrantedAuthority("SCOPE_iam.read"));

    private final RequestPostProcessor usersReadPermissionJwt = jwt()
            .jwt(token -> token
                    .claim("tenant_id", TENANT_ID.toString())
                    .claim("effective_roles", List.of())
                    .claim("effective_permissions", List.of("iam.users.read"))
                    .claim("aud", List.of("identityforge-admin-api"))
                    .claim("scope", "iam.read"))
            .authorities(new SimpleGrantedAuthority("SCOPE_iam.read"));

    private final RequestPostProcessor fakePermissionJwt = jwt()
            .jwt(token -> token
                    .claim("tenant_id", TENANT_ID.toString())
                    .claim("effective_roles", List.of())
                    .claim("effective_permissions", List.of("iam.fake.admin"))
                    .claim("aud", List.of("identityforge-admin-api"))
                    .claim("scope", "iam.read"))
            .authorities(new SimpleGrantedAuthority("SCOPE_iam.read"));

    private RequestPostProcessor adminJwt(Set<String> roles, Set<String> permissions, String scope) {
        return jwt()
                .jwt(token -> token
                        .claim("tenant_id", TENANT_ID.toString())
                        .claim("effective_roles", roles)
                        .claim("effective_permissions", permissions)
                        .claim("aud", List.of("identityforge-admin-api"))
                        .claim("scope", scope))
                .authorities(new SimpleGrantedAuthority("SCOPE_" + scope.split(" ")[0]));
    }

    private RequestPostProcessor platformOperatorJwt(String scope) {
        return jwt()
                .jwt(token -> token
                        .claim("tenant_id", TENANT_ID.toString())
                        .claim("platform_operator", true)
                        .claim("effective_permissions", List.of())
                        .claim("aud", List.of("identityforge-admin-api"))
                        .claim("scope", scope))
                .authorities(new SimpleGrantedAuthority("SCOPE_" + scope.split(" ")[0]));
    }

    @Test
    void currentUserRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void publicAuthorizationEndpointUsesInteractiveLoginEntryPointRegardlessOfAcceptHeader() throws Exception {
        RegisteredClient consoleClient = RegisteredClient.withId(CLIENT_ID.toString())
                .clientId("identityforge-console")
                .clientName("IdentityForge Console")
                .clientAuthenticationMethod(ClientAuthenticationMethod.NONE)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("http://localhost:5173/oauth2/callback")
                .scope("openid")
                .scope("profile")
                .scope("iam.read")
                .scope("iam.write")
                .clientSettings(ClientSettings.builder().requireProofKey(true).build())
                .build();
        when(registeredClientRepository.findByClientId("identityforge-console")).thenReturn(consoleClient);

        mockMvc.perform(get("/oauth2/authorize")
                        .accept(MediaType.APPLICATION_JSON)
                        .queryParam("response_type", "code")
                        .queryParam("client_id", "identityforge-console")
                        .queryParam("redirect_uri", "http://localhost:5173/oauth2/callback")
                        .queryParam("scope", "openid profile iam.read iam.write")
                        .queryParam("state", "entry-point-regression-state")
                        .queryParam("code_challenge", "0123456789012345678901234567890123456789012")
                        .queryParam("code_challenge_method", "S256"))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("http://localhost/login"));
    }

    @Test
    void currentUserReturnsSafePrincipalInformation() throws Exception {
        mockMvc.perform(get("/api/me")
                        .with(jwt().jwt(token -> token
                                        .subject(USER_ID.toString())
                                        .claim("user_id", USER_ID.toString())
                                        .claim("tenant_id", TENANT_ID.toString())
                                        .claim("preferred_username", "admin")
                                        .claim("display_name", "Development Super Admin")
                                        .claim("platform_operator", true)
                                        .claim("roles", List.of("platform-admin"))
                                        .claim("direct_roles", List.of("platform-admin"))
                                        .claim("group_roles", List.of())
                                        .claim("effective_roles", List.of("platform-admin"))
                                        .claim("effective_permissions", List.of("iam.admin"))
                                        .claim("scope", "iam.read iam.write"))
                                .authorities(new SimpleGrantedAuthority("SCOPE_iam.read"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subject").value(USER_ID.toString()))
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
        when(userSecurityStateService.isTokenStateCurrent(USER_ID, 1)).thenReturn(true);
        PlatformUserDetails principal = new PlatformUserDetails(
                USER_ID,
                TENANT_ID,
                "admin",
                "Development Super Admin",
                "{noop}not-exposed",
                AccountStatus.ACTIVE,
                Set.of("platform-admin"),
                Set.of("iam.users.read"));

        mockMvc.perform(post("/logout")
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf())
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
                                .user(principal)))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("http://localhost:5173/login?loggedOut=true"))
                .andExpect(header().string("Set-Cookie", containsString("JSESSIONID=;")))
                .andExpect(header().string("Set-Cookie", containsString("Max-Age=0")));

        verify(auditApplicationService).recordEvent(TENANT_ID, "USER_LOGGED_OUT", "USER", USER_ID);
        verify(authorizationLifecycleService).revokeUserClientAuthorizations(USER_ID, "identityforge-console");
    }

    @Test
    void browserLogoutRequiresPostAndCsrf() throws Exception {
        when(userSecurityStateService.isTokenStateCurrent(USER_ID, 1)).thenReturn(true);
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
                .andExpect(status().isOk());

        mockMvc.perform(post("/logout").with(
                        org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
                                .user(principal)))
                .andExpect(status().isForbidden());
    }

    @Test
    void createsTenant() throws Exception {
        when(tenantApplicationService.createTenant(eq("Acme"), eq("acme")))
                .thenReturn(tenant("Acme"));

        mockMvc.perform(post("/api/tenants")
                        .with(writeScopeJwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Acme","slug":"acme"}
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
        when(tenantApplicationService.createTenant(eq("Acme"), eq("acme")))
                .thenThrow(new ValidationException("Tenant slug already exists: acme"));

        mockMvc.perform(post("/api/tenants")
                        .with(writeScopeJwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Acme","slug":"acme"}
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
    void validAdminPermissionCanReadExpectedApiPath() throws Exception {
        when(userApplicationService.listUsers(eq(TENANT_ID), any(Pageable.class)))
                .thenReturn(pageOf(user("alice", "Alice Example")));

        mockMvc.perform(get("/api/users")
                        .queryParam("tenantId", TENANT_ID.toString())
                        .with(usersReadPermissionJwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].username").value("alice"));
    }

    @Test
    void platformAdminCanUseReadAndWriteAdminApisWithoutConcretePermissionClaims() throws Exception {
        when(userApplicationService.listUsers(eq(TENANT_ID), any(Pageable.class)))
                .thenReturn(pageOf(user("platform-reader", "Platform Reader")));
        when(userApplicationService.createUser(eq(TENANT_ID), eq("platform-writer"), eq("Platform Writer")))
                .thenReturn(user("platform-writer", "Platform Writer"));

        RequestPostProcessor platformAdmin = platformOperatorJwt("iam.read iam.write");

        mockMvc.perform(get("/api/users")
                        .queryParam("tenantId", TENANT_ID.toString())
                        .with(platformAdmin))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/users")
                        .with(platformAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "tenantId":"00000000-0000-0000-0000-000000000001",
                                  "username":"platform-writer",
                                  "displayName":"Platform Writer"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value("platform-writer"));
    }

    @Test
    void auditorCanReadButCannotWriteUsers() throws Exception {
        when(userApplicationService.listUsers(eq(TENANT_ID), any(Pageable.class)))
                .thenReturn(pageOf(user("audited-user", "Audited User")));

        RequestPostProcessor auditor = adminJwt(
                Set.of("auditor"),
                Set.of("iam.users.read", "iam.groups.read", "iam.roles.read", "iam.permissions.read", "iam.clients.read", "iam.audit.read"),
                "iam.read iam.write");

        mockMvc.perform(get("/api/users")
                        .queryParam("tenantId", TENANT_ID.toString())
                        .with(auditor))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/users")
                        .with(auditor)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "tenantId":"00000000-0000-0000-0000-000000000001",
                                  "username":"blocked-auditor",
                                  "displayName":"Blocked Auditor"
                                }
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void usersReadPermissionCannotWriteUsers() throws Exception {
        mockMvc.perform(post("/api/users")
                        .with(adminJwt(Set.of(), Set.of("iam.users.read"), "iam.read iam.write"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "tenantId":"00000000-0000-0000-0000-000000000001",
                                  "username":"read-only-user",
                                  "displayName":"Read Only User"
                                }
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void usersWritePermissionCanCreateUserInOwnTenant() throws Exception {
        when(userApplicationService.createUser(eq(TENANT_ID), eq("writer"), eq("Writer")))
                .thenReturn(user("writer", "Writer"));

        mockMvc.perform(post("/api/users")
                        .with(adminJwt(Set.of(), Set.of("iam.users.write"), "iam.write"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "tenantId":"00000000-0000-0000-0000-000000000001",
                                  "username":"writer",
                                  "displayName":"Writer"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value("writer"));
    }

    @Test
    void clientsReadPermissionCannotCreateClients() throws Exception {
        mockMvc.perform(post("/api/clients")
                        .with(adminJwt(Set.of(), Set.of("iam.clients.read"), "iam.read iam.write"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "tenantId":"00000000-0000-0000-0000-000000000001",
                                  "clientId":"blocked-client",
                                  "name":"Blocked Client"
                                }
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void resourceServersReadPermissionCanListApplications() throws Exception {
        when(resourceServerApplicationService.listResourceServers(eq(TENANT_ID), any(Pageable.class)))
                .thenReturn(pageOf(resourceServer("payroll-api", "Payroll API")));

        mockMvc.perform(get("/api/resource-servers")
                        .queryParam("tenantId", TENANT_ID.toString())
                        .with(adminJwt(Set.of(), Set.of("iam.resource-servers.read"), "iam.read")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(RESOURCE_SERVER_ID.toString()))
                .andExpect(jsonPath("$.items[0].tenantId").value(TENANT_ID.toString()))
                .andExpect(jsonPath("$.items[0].identifier").value("payroll-api"));
    }

    @Test
    void auditorCanReadButCannotCreateResourceServers() throws Exception {
        when(resourceServerApplicationService.listResourceServers(eq(TENANT_ID), any(Pageable.class)))
                .thenReturn(pageOf(resourceServer("audit-api", "Audit API")));

        RequestPostProcessor auditor = adminJwt(
                Set.of("auditor"),
                Set.of("iam.resource-servers.read"),
                "iam.read iam.write");

        mockMvc.perform(get("/api/resource-servers")
                        .queryParam("tenantId", TENANT_ID.toString())
                        .with(auditor))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/resource-servers")
                        .with(auditor)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "tenantId":"00000000-0000-0000-0000-000000000001",
                                  "identifier":"blocked-api",
                                  "name":"Blocked API"
                                }
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void tenantAdminCanManageOwnTenantResourceServers() throws Exception {
        when(resourceServerApplicationService.createResourceServer(
                        eq(TENANT_ID),
                        eq("payroll-api"),
                        eq("Payroll API"),
                        eq("Payroll capabilities")))
                .thenReturn(resourceServer("payroll-api", "Payroll API"));

        mockMvc.perform(post("/api/resource-servers")
                        .with(adminJwt(Set.of("tenant-admin"), Set.of("iam.resource-servers.write"), "iam.write"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "tenantId":"00000000-0000-0000-0000-000000000001",
                                  "identifier":"payroll-api",
                                  "name":"Payroll API",
                                  "description":"Payroll capabilities"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.identifier").value("payroll-api"));
    }

    @Test
    void tenantAdminCannotManageAnotherTenantsResourceServers() throws Exception {
        when(resourceServerApplicationService.createResourceServer(
                        eq(OTHER_TENANT_ID),
                        eq("other-api"),
                        eq("Other API"),
                        any()))
                .thenThrow(new AccessDeniedException("Tenant administrators can only access their own tenant"));

        mockMvc.perform(post("/api/resource-servers")
                        .with(adminJwt(Set.of("tenant-admin"), Set.of("iam.resource-servers.write"), "iam.write"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "tenantId":"00000000-0000-0000-0000-000000000009",
                                  "identifier":"other-api",
                                  "name":"Other API"
                                }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("access_denied"))
                .andExpect(jsonPath("$.message").value("Tenant administrators can only access their own tenant"));
    }

    @Test
    void platformAdminCanManageResourceServers() throws Exception {
        ResourceServer resourceServer = resourceServer("platform-api", "Platform API");
        resourceServer.setStatus(ResourceServerStatus.DISABLED);
        when(resourceServerApplicationService.disableResourceServer(eq(RESOURCE_SERVER_ID)))
                .thenReturn(resourceServer);

        mockMvc.perform(post("/api/resource-servers/{resourceServerId}/disable", RESOURCE_SERVER_ID)
                        .with(platformOperatorJwt("iam.write")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DISABLED"));
    }

    @Test
    void resourceServerWritePermissionIsRequiredForApplicationWrites() throws Exception {
        mockMvc.perform(post("/api/resource-servers")
                        .with(adminJwt(Set.of(), Set.of("iam.resource-servers.read"), "iam.read iam.write"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "tenantId":"00000000-0000-0000-0000-000000000001",
                                  "identifier":"read-only-api",
                                  "name":"Read Only API"
                                }
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void managesResourcePermissionsUnderApplication() throws Exception {
        ResourceServer resourceServer = resourceServer("payroll-api", "Payroll API");
        ResourcePermission permission = resourcePermission(resourceServer, "payroll.employee.read");
        when(resourceServerApplicationService.listResourcePermissions(eq(RESOURCE_SERVER_ID)))
                .thenReturn(List.of(permission));
        when(resourceServerApplicationService.createResourcePermission(
                        eq(RESOURCE_SERVER_ID),
                        eq("payroll.salary.write"),
                        eq("Write salary"),
                        eq("Write salary records")))
                .thenReturn(resourcePermission(resourceServer, "payroll.salary.write"));

        mockMvc.perform(get("/api/resource-servers/{resourceServerId}/permissions", RESOURCE_SERVER_ID)
                        .with(adminJwt(Set.of(), Set.of("iam.resource-servers.read"), "iam.read")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("payroll.employee.read"))
                .andExpect(jsonPath("$[0].resourceServerId").value(RESOURCE_SERVER_ID.toString()));
        mockMvc.perform(post("/api/resource-servers/{resourceServerId}/permissions", RESOURCE_SERVER_ID)
                        .with(adminJwt(Set.of(), Set.of("iam.resource-servers.write"), "iam.write"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name":"payroll.salary.write",
                                  "displayName":"Write salary",
                                  "description":"Write salary records"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("payroll.salary.write"));
    }

    @Test
    void auditReadPermissionCanReadAuditLogs() throws Exception {
        when(auditApplicationService.listAuditLogs(eq(TENANT_ID), any(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(pageOf(auditLog()));

        mockMvc.perform(get("/api/audit-logs")
                        .queryParam("tenantId", TENANT_ID.toString())
                        .with(adminJwt(Set.of(), Set.of("iam.audit.read"), "iam.read")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(AUDIT_ID.toString()));
    }

    @Test
    void auditReadPermissionIsRequiredForAuditLogs() throws Exception {
        mockMvc.perform(get("/api/audit-logs")
                        .queryParam("tenantId", TENANT_ID.toString())
                        .with(adminJwt(Set.of(), Set.of("iam.users.read"), "iam.read")))
                .andExpect(status().isForbidden());
    }

    @Test
    void normalUserWithoutAdminRoleOrPermissionCannotAccessAdminApi() throws Exception {
        mockMvc.perform(get("/api/users")
                        .queryParam("tenantId", TENANT_ID.toString())
                        .with(adminJwt(Set.of("employee"), Set.of(), "iam.read")))
                .andExpect(status().isForbidden());
    }

    @Test
    void tenantAdminStillCannotAccessAnotherTenant() throws Exception {
        when(userApplicationService.listUsers(eq(OTHER_TENANT_ID), any(Pageable.class)))
                .thenThrow(new AccessDeniedException("Tenant administrators can only access their own tenant"));

        mockMvc.perform(get("/api/users")
                        .queryParam("tenantId", OTHER_TENANT_ID.toString())
                        .with(adminJwt(Set.of("tenant-admin"), Set.of("iam.users.read"), "iam.read")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("access_denied"))
                .andExpect(jsonPath("$.message").value("Tenant administrators can only access their own tenant"));
    }

    @Test
    void iamAdminPermissionImpliesAllAdminApiPermissions() throws Exception {
        when(clientApplicationService.createClientWithSecret(
                        eq(TENANT_ID),
                        eq("iam-admin-client"),
                        eq("IAM Admin Client"),
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                        any()))
                .thenReturn(new ClientSecretResult(client("iam-admin-client", "IAM Admin Client"), "raw-client-secret-once"));

        mockMvc.perform(post("/api/clients")
                        .with(adminJwt(Set.of(), Set.of("iam.admin"), "iam.write"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "tenantId":"00000000-0000-0000-0000-000000000001",
                                  "clientId":"iam-admin-client",
                                  "name":"IAM Admin Client"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.client.clientId").value("iam-admin-client"));
    }

    @Test
    void clientCredentialsTokenDoesNotBypassAdminRbac() throws Exception {
        mockMvc.perform(get("/api/users")
                        .queryParam("tenantId", TENANT_ID.toString())
                        .with(jwt().jwt(token -> token
                                        .subject("identityforge-dev")
                                        .claim("scope", "iam.read iam.write")
                                        .claim("grant_type", "client_credentials"))
                                .authorities(new SimpleGrantedAuthority("SCOPE_iam.read"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void fakeIamPermissionCannotAccessAdminApi() throws Exception {
        mockMvc.perform(get("/api/users")
                        .queryParam("tenantId", TENANT_ID.toString())
                        .with(fakePermissionJwt))
                .andExpect(status().isForbidden());
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
        when(permissionApplicationService.createPermission(eq("clients:read")))
                .thenReturn(permission("clients:read"));

        mockMvc.perform(post("/api/permissions")
                        .with(writeScopeJwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name":"clients:read"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(PERMISSION_ID.toString()))
                .andExpect(jsonPath("$.tenantId").doesNotExist())
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
        when(permissionApplicationService.listPermissions(any(Pageable.class)))
                .thenReturn(pageOf(permission("users:read")));

        mockMvc.perform(get("/api/roles").queryParam("tenantId", TENANT_ID.toString()).with(readScopeJwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(ROLE_ID.toString()))
                .andExpect(jsonPath("$.totalElements").value(1));
        mockMvc.perform(get("/api/permissions").with(readScopeJwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(PERMISSION_ID.toString()))
                .andExpect(jsonPath("$.items[0].tenantId").doesNotExist())
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
                .andExpect(jsonPath("$.items[0].allowedResourcePermissions").isArray())
                .andExpect(jsonPath("$.totalElements").value(1));
        mockMvc.perform(get("/api/clients/{clientId}", CLIENT_ID).with(readScopeJwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(CLIENT_ID.toString()))
                .andExpect(jsonPath("$.clientSecretHash").doesNotExist());
    }

    @Test
    void managesClientResourcePermissionsThroughApi() throws Exception {
        Client client = client("application-client", "Application Client");
        ResourceServer resourceServer = resourceServer("payroll-api", "Payroll API");
        ResourcePermission permission = resourcePermission(resourceServer, "payroll.employee.read");
        client.setResourceServer(resourceServer);
        client.addAllowedResourcePermission(permission);
        when(clientApplicationService.listAllowedResourcePermissions(eq(CLIENT_ID)))
                .thenReturn(Set.of(permission));
        when(clientApplicationService.assignResourcePermissionToClient(eq(CLIENT_ID), eq(RESOURCE_PERMISSION_ID)))
                .thenReturn(client);
        when(clientApplicationService.removeResourcePermissionFromClient(eq(CLIENT_ID), eq(RESOURCE_PERMISSION_ID)))
                .thenReturn(client);

        mockMvc.perform(get("/api/clients/{clientId}/resource-permissions", CLIENT_ID)
                        .with(readScopeJwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("payroll.employee.read"));
        mockMvc.perform(post("/api/clients/{clientId}/resource-permissions/{permissionId}", CLIENT_ID, RESOURCE_PERMISSION_ID)
                        .with(writeScopeJwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resourceServerId").value(RESOURCE_SERVER_ID.toString()))
                .andExpect(jsonPath("$.resourceServerName").value("Payroll API"))
                .andExpect(jsonPath("$.allowedResourcePermissions[0].name").value("payroll.employee.read"))
                .andExpect(jsonPath("$.clientSecretHash").doesNotExist());
        mockMvc.perform(delete("/api/clients/{clientId}/resource-permissions/{permissionId}", CLIENT_ID, RESOURCE_PERMISSION_ID)
                        .with(writeScopeJwt))
                .andExpect(status().isOk());
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
                        any(),
                        any(),
                        eq(false)))
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
        User scimUser = user("scim-user", "SCIM User");
        scimUser.setAccountStatus(AccountStatus.ACTIVE);
        when(scimProvisioningService.createUser(eq(TENANT_ID), any())).thenReturn(scimUser);

        mockMvc.perform(post("/scim/v2/{tenantId}/Users", TENANT_ID)
                        .with(writeScopeJwt)
                        .contentType("application/scim+json")
                        .content("""
                                {
                                  "schemas":["urn:ietf:params:scim:schemas:core:2.0:User"],
                                  "userName":"scim-user",
                                  "displayName":"SCIM User"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, containsString("application/scim+json")))
                .andExpect(header().string(HttpHeaders.LOCATION, containsString("/scim/v2/" + TENANT_ID + "/Users/" + USER_ID)))
                .andExpect(header().string(HttpHeaders.ETAG, "\"0\""))
                .andExpect(jsonPath("$.schemas[0]").value("urn:ietf:params:scim:schemas:core:2.0:User"))
                .andExpect(jsonPath("$.id").value(USER_ID.toString()))
                .andExpect(jsonPath("$.userName").value("scim-user"))
                .andExpect(jsonPath("$.displayName").value("SCIM User"))
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.meta.resourceType").value("User"))
                .andExpect(jsonPath("$.meta.location").value(containsString("/Users/" + USER_ID)))
                .andExpect(jsonPath("$.mfaSecret").doesNotExist());
    }

    @Test
    void readsScimUser() throws Exception {
        User user = user("read-scim-user", "Read SCIM User");
        when(scimProvisioningService.getUser(eq(TENANT_ID), eq(USER_ID)))
                .thenReturn(user);

        mockMvc.perform(get("/scim/v2/{tenantId}/Users/{id}", TENANT_ID, USER_ID)
                        .with(readScopeJwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(USER_ID.toString()))
                .andExpect(jsonPath("$.userName").value("read-scim-user"))
                .andExpect(jsonPath("$.mfaSecret").doesNotExist());
    }

    @Test
    void listsScimUsersWithStandardPaginationAndFilterEnvelope() throws Exception {
        User user = user("filtered-user", "Filtered User");
        when(scimProvisioningService.listUsers(
                        TENANT_ID, "userName eq \"filtered-user\"", 3, 25))
                .thenReturn(new ScimResultPage<>(7, List.of(user)));

        mockMvc.perform(get("/scim/v2/{tenantId}/Users", TENANT_ID)
                        .queryParam("filter", "userName eq \"filtered-user\"")
                        .queryParam("startIndex", "3")
                        .queryParam("count", "25")
                        .with(readScopeJwt))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, containsString("application/scim+json")))
                .andExpect(jsonPath("$.schemas[0]").value("urn:ietf:params:scim:api:messages:2.0:ListResponse"))
                .andExpect(jsonPath("$.totalResults").value(7))
                .andExpect(jsonPath("$.startIndex").value(3))
                .andExpect(jsonPath("$.itemsPerPage").value(1))
                .andExpect(jsonPath("$.Resources[0].userName").value("filtered-user"));
    }

    @Test
    void exposesScimSupportedCapabilitiesToUserReaders() throws Exception {
        mockMvc.perform(get("/scim/v2/{tenantId}/ServiceProviderConfig", TENANT_ID)
                        .with(usersReadPermissionJwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.schemas[0]")
                        .value("urn:ietf:params:scim:schemas:core:2.0:ServiceProviderConfig"))
                .andExpect(jsonPath("$.patch.supported").value(true))
                .andExpect(jsonPath("$.filter.maxResults").value(100))
                .andExpect(jsonPath("$.bulk.supported").value(false))
                .andExpect(jsonPath("$.changePassword.supported").value(false))
                .andExpect(jsonPath("$.etag.supported").value(true));
    }

    @Test
    void createsScimGroup() throws Exception {
        Group group = group("engineering");
        group.addUser(user("scim-member", "SCIM Member"));
        when(scimProvisioningService.createGroup(eq(TENANT_ID), any())).thenReturn(group);

        mockMvc.perform(post("/scim/v2/{tenantId}/Groups", TENANT_ID)
                        .with(writeScopeJwt)
                        .contentType("application/scim+json")
                        .content("""
                                {
                                  "schemas":["urn:ietf:params:scim:schemas:core:2.0:Group"],
                                  "displayName":"engineering",
                                  "members":[{"value":"00000000-0000-0000-0000-000000000002","type":"User"}]
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, containsString("application/scim+json")))
                .andExpect(jsonPath("$.schemas[0]").value("urn:ietf:params:scim:schemas:core:2.0:Group"))
                .andExpect(jsonPath("$.id").value(GROUP_ID.toString()))
                .andExpect(jsonPath("$.displayName").value("engineering"))
                .andExpect(jsonPath("$.members[0].value").value(USER_ID.toString()));
    }

    @Test
    void readsScimGroup() throws Exception {
        Group group = group("readers");
        group.addUser(user("reader", "Reader User"));
        when(scimProvisioningService.getGroup(eq(TENANT_ID), eq(GROUP_ID)))
                .thenReturn(group);

        mockMvc.perform(get("/scim/v2/{tenantId}/Groups/{id}", TENANT_ID, GROUP_ID)
                        .with(readScopeJwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(GROUP_ID.toString()))
                .andExpect(jsonPath("$.displayName").value("readers"))
                .andExpect(jsonPath("$.members[0].display").value("Reader User"));
    }

    @Test
    void patchesScimGroupMembershipUsingCapitalizedOperationsField() throws Exception {
        Group group = group("engineering");
        group.addUser(user("member", "Member User"));
        when(scimProvisioningService.patchGroup(eq(TENANT_ID), eq(GROUP_ID), any(), eq("\"0\"")))
                .thenReturn(group);

        mockMvc.perform(patch("/scim/v2/{tenantId}/Groups/{id}", TENANT_ID, GROUP_ID)
                        .with(writeScopeJwt)
                        .header(HttpHeaders.IF_MATCH, "\"0\"")
                        .contentType("application/scim+json")
                        .content("""
                                {
                                  "schemas":["urn:ietf:params:scim:api:messages:2.0:PatchOp"],
                                  "Operations":[{
                                    "op":"add",
                                    "path":"members",
                                    "value":[{"value":"00000000-0000-0000-0000-000000000002"}]
                                  }]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ETAG, "\"0\""))
                .andExpect(jsonPath("$.members[0].value").value(USER_ID.toString()));
        verify(scimProvisioningService).patchGroup(eq(TENANT_ID), eq(GROUP_ID), any(), eq("\"0\""));
    }

    @Test
    void scimPatchAndDeleteRequireWriteScope() throws Exception {
        mockMvc.perform(patch("/scim/v2/{tenantId}/Users/{id}", TENANT_ID, USER_ID)
                        .with(readScopeJwt)
                        .contentType("application/scim+json")
                        .content("""
                                {"schemas":["urn:ietf:params:scim:api:messages:2.0:PatchOp"],
                                 "Operations":[{"op":"replace","path":"active","value":false}]}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, containsString("application/scim+json")))
                .andExpect(jsonPath("$.schemas[0]").value("urn:ietf:params:scim:api:messages:2.0:Error"));
        mockMvc.perform(delete("/scim/v2/{tenantId}/Groups/{id}", TENANT_ID, GROUP_ID)
                        .with(readScopeJwt))
                .andExpect(status().isForbidden());
    }

    @Test
    void returnsAndAuditsScimProtocolErrors() throws Exception {
        when(scimProvisioningService.listUsers(TENANT_ID, "displayName co \"Ali\"", 1, 50))
                .thenThrow(ScimProtocolException.invalidFilter("Only eq is supported"));

        mockMvc.perform(get("/scim/v2/{tenantId}/Users", TENANT_ID)
                        .queryParam("filter", "displayName co \"Ali\"")
                        .with(readScopeJwt))
                .andExpect(status().isBadRequest())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, containsString("application/scim+json")))
                .andExpect(jsonPath("$.schemas[0]").value("urn:ietf:params:scim:api:messages:2.0:Error"))
                .andExpect(jsonPath("$.status").value("400"))
                .andExpect(jsonPath("$.scimType").value("invalidFilter"))
                .andExpect(jsonPath("$.detail").value("Only eq is supported"));
        verify(auditApplicationService).recordFailure(
                TENANT_ID, "SCIM_REQUEST_REJECTED", "TENANT", TENANT_ID, "INVALID_FILTER");
    }

    @Test
    void mapsScimUniquenessAndJsonFailuresToStandardErrors() throws Exception {
        when(scimProvisioningService.createUser(eq(TENANT_ID), any()))
                .thenThrow(new ValidationException("Username already exists in tenant"));

        mockMvc.perform(post("/scim/v2/{tenantId}/Users", TENANT_ID)
                        .with(writeScopeJwt)
                        .contentType("application/scim+json")
                        .content("""
                                {"schemas":["urn:ietf:params:scim:schemas:core:2.0:User"],
                                 "userName":"duplicate"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value("409"))
                .andExpect(jsonPath("$.scimType").value("uniqueness"));

        mockMvc.perform(post("/scim/v2/{tenantId}/Users", TENANT_ID)
                        .with(writeScopeJwt)
                        .contentType("application/scim+json")
                        .content("{not-json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("400"))
                .andExpect(jsonPath("$.scimType").value("invalidSyntax"));
    }

    @Test
    void auditsCrossTenantScimRejectionAgainstTheCallerTenant() throws Exception {
        when(scimProvisioningService.getUser(OTHER_TENANT_ID, USER_ID))
                .thenThrow(new AccessDeniedException("cross tenant"));

        mockMvc.perform(get("/scim/v2/{tenantId}/Users/{id}", OTHER_TENANT_ID, USER_ID)
                        .with(usersReadPermissionJwt))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("The requested tenant resource is not accessible"));
        verify(auditApplicationService).recordFailure(
                TENANT_ID, "SCIM_REQUEST_REJECTED", "TENANT", TENANT_ID, "HTTP_403");
    }

    @Test
    void managesGroupsAndMembers() throws Exception {
        Group group = group("engineering");
        group.addUser(user("member", "Member User"));
        when(groupApplicationService.createGroup(
                        eq(TENANT_ID), eq("engineering"), eq("Engineering"), eq("Platform team")))
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
                .thenReturn(new MfaVerificationResult(USER_ID, true, List.of("ABCD-EFGH-JKLM-NPQR")));
        when(mfaApplicationService.getStatus(eq(USER_ID)))
                .thenReturn(
                        new MfaStatus(USER_ID, true, true, false, 10, 10),
                        new MfaStatus(USER_ID, false, false, false, 0, 0));
        when(mfaApplicationService.regenerateRecoveryCodes(eq(USER_ID)))
                .thenReturn(new MfaRecoveryCodesResult(USER_ID, List.of("WXYZ-2345-6789-ABCD")));

        mockMvc.perform(post("/api/users/{userId}/mfa/totp/enrollment", USER_ID).with(writeScopeJwt))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("no-store")))
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
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("no-store")))
                .andExpect(jsonPath("$.verified").value(true))
                .andExpect(jsonPath("$.recoveryCodes[0]").value("ABCD-EFGH-JKLM-NPQR"))
                .andExpect(jsonPath("$.secret").doesNotExist())
                .andExpect(jsonPath("$.secretCiphertext").doesNotExist());
        mockMvc.perform(get("/api/users/{userId}/mfa/totp", USER_ID).with(readScopeJwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totpVerified").value(true))
                .andExpect(jsonPath("$.recoveryCodesRemaining").value(10))
                .andExpect(jsonPath("$.secret").doesNotExist());
        mockMvc.perform(post("/api/users/{userId}/mfa/totp/recovery-codes", USER_ID).with(writeScopeJwt))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("no-store")))
                .andExpect(jsonPath("$.recoveryCodes[0]").value("WXYZ-2345-6789-ABCD"))
                .andExpect(jsonPath("$.codeHash").doesNotExist());
        mockMvc.perform(delete("/api/users/{userId}/mfa/totp", USER_ID).with(writeScopeJwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totpEnrolled").value(false))
                .andExpect(jsonPath("$.recoveryCodesRemaining").value(0));
        verify(mfaApplicationService).disableTotp(USER_ID);
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
        mockMvc.perform(post("/scim/v2/{tenantId}/Users", TENANT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "userName":"scim-user",
                                  "displayName":"SCIM User"
                                }
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, containsString("application/scim+json")))
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"))
                .andExpect(jsonPath("$.schemas[0]").value("urn:ietf:params:scim:api:messages:2.0:Error"))
                .andExpect(jsonPath("$.status").value("401"));
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
                .andExpect(status().isForbidden())
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
        Permission permission = Permission.create(name);
        permission.setId(PERMISSION_ID);
        return permission;
    }

    private Client client(String clientId, String name) {
        Client client = Client.create(tenant("Test Tenant"), clientId, name);
        client.setId(CLIENT_ID);
        return client;
    }

    private ResourceServer resourceServer(String identifier, String name) {
        ResourceServer resourceServer = ResourceServer.create(tenant("Test Tenant"), identifier, name);
        resourceServer.setId(RESOURCE_SERVER_ID);
        return resourceServer;
    }

    private ResourcePermission resourcePermission(ResourceServer resourceServer, String name) {
        ResourcePermission permission = ResourcePermission.create(resourceServer, name, name, null);
        permission.setId(RESOURCE_PERMISSION_ID);
        return permission;
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

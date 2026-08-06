package io.github.doubletree.iam.authorization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.doubletree.iam.application.result.ClientSecretResult;
import io.github.doubletree.iam.application.service.ClientApplicationService;
import io.github.doubletree.iam.application.service.GroupApplicationService;
import io.github.doubletree.iam.application.service.ResourceServerApplicationService;
import io.github.doubletree.iam.application.service.SystemPermissionCatalogService;
import io.github.doubletree.iam.application.service.TenantApplicationService;
import io.github.doubletree.iam.application.service.UserApplicationService;
import io.github.doubletree.iam.domain.ClientType;
import io.github.doubletree.iam.domain.ResourcePermission;
import io.github.doubletree.iam.domain.ResourceServer;
import io.github.doubletree.iam.domain.Role;
import io.github.doubletree.iam.domain.Tenant;
import io.github.doubletree.iam.domain.User;
import io.github.doubletree.iam.repository.AuditLogRepository;
import io.github.doubletree.iam.repository.RoleRepository;
import java.net.URLDecoder;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.util.UriComponentsBuilder;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(properties = "spring.application.name=identityforge")
@AutoConfigureMockMvc
@Testcontainers
class OAuth2AuthorizationCodeLoginFlowTests {

    private static final String REDIRECT_URI = "http://127.0.0.1:8080/oauth2/demo/callback";
    private static final String RAW_PASSWORD = "correct-password-123";

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtDecoder jwtDecoder;

    @Autowired
    private TenantApplicationService tenantApplicationService;

    @Autowired
    private UserApplicationService userApplicationService;

    @Autowired
    private ClientApplicationService clientApplicationService;

    @Autowired
    private ResourceServerApplicationService resourceServerApplicationService;

    @Autowired
    private GroupApplicationService groupApplicationService;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private RoleRepository roleRepository;

    @DynamicPropertySource
    static void registerDataSourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", postgres::getDriverClassName);
    }

    @Test
    void persistedConfidentialClientCompletesAuthorizationCodeLoginFlowAndAccessesProtectedApi()
            throws Exception {
        FlowFixture fixture = createFlowFixture();

        mockMvc.perform(get("/api/users/{userId}", fixture.user().getId()))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"));

        StartedAuthorization startedAuthorization = startAuthorizationRequestExpectingLogin(
                fixture.client().client().getClientId(), "iam.read", "read-state");
        AuthenticatedSession authenticatedSession =
                login(startedAuthorization.session(), fixture.user().getUsername());

        String readCode = continueAuthorizationRequestAndExtractCode(
                authenticatedSession.session(), authenticatedSession.authorizationRedirect(), "read-state");
        String readAccessToken = exchangeCodeForAccessToken(
                fixture.client().client().getClientId(),
                fixture.client().clientSecret(),
                readCode);

        Jwt readJwt = jwtDecoder.decode(readAccessToken);
        assertThat(readJwt.getTokenValue().split("\\.")).hasSize(3);
        assertThat(readJwt.getSubject()).isEqualTo(fixture.user().getUsername());
        assertThat(readJwt.getAudience()).contains(fixture.client().client().getClientId());
        assertThat(readJwt.getClaimAsStringList("scope")).containsExactly("iam.read");

        mockMvc.perform(get("/api/users/{userId}", fixture.user().getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + readAccessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(fixture.user().getId().toString()))
                .andExpect(jsonPath("$.username").value(fixture.user().getUsername()))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());

        String writeOnlyCode = authorizeAuthenticatedSessionAndExtractCode(
                authenticatedSession.session(), fixture.client().client().getClientId(), "iam.write", "write-state");
        String writeOnlyAccessToken = exchangeCodeForAccessToken(
                fixture.client().client().getClientId(),
                fixture.client().clientSecret(),
                writeOnlyCode);

        mockMvc.perform(get("/api/users/{userId}", fixture.user().getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + writeOnlyAccessToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void consentPageApprovalAndDenialUseOAuth2ErrorBehaviorAndAuditEvents() throws Exception {
        auditLogRepository.deleteAll();
        FlowFixture fixture = createFlowFixture(true);

        StartedAuthorization approvalStart = startAuthorizationRequestExpectingLogin(
                fixture.client().client().getClientId(), "iam.read iam.write", "approval-state");
        AuthenticatedSession approvalSession = login(approvalStart.session(), fixture.user().getUsername());
        String approvalConsentUrl = expectConsentRedirect(
                approvalSession.session(), approvalSession.authorizationRedirect());

        mockMvc.perform(get(URI.create(approvalConsentUrl)).session(approvalSession.session()))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Authorize OAuth Flow Demo Client")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("iam.read")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("iam.write")))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("clientSecret"))));

        String approvalRedirect = submitConsent(
                approvalSession.session(), approvalConsentUrl, fixture.client().client().getClientId(), true);
        assertThat(extractCode(approvalRedirect, "approval-state")).isNotBlank();

        FlowFixture denialFixture = createFlowFixture(true);
        StartedAuthorization denialStart = startAuthorizationRequestExpectingLogin(
                denialFixture.client().client().getClientId(), "iam.read iam.write", "denial-state");
        AuthenticatedSession denialSession = login(denialStart.session(), denialFixture.user().getUsername());
        String denialConsentUrl = expectConsentRedirect(denialSession.session(), denialSession.authorizationRedirect());
        String denialRedirect = submitConsent(
                denialSession.session(), denialConsentUrl, denialFixture.client().client().getClientId(), false);
        var denialParams = UriComponentsBuilder.fromUriString(denialRedirect).build().getQueryParams();

        assertThat(denialParams.getFirst("error")).isEqualTo("access_denied");
        assertThat(denialParams.getFirst("code")).isNull();
        assertThat(denialRedirect).doesNotContain(fixture.client().clientSecret(), denialFixture.client().clientSecret());
        assertThat(auditLogRepository.findByAction("OAUTH2_CONSENT_APPROVED")).isNotEmpty();
        assertThat(auditLogRepository.findByAction("OAUTH2_CONSENT_DENIED")).isNotEmpty();
    }

    @Test
    void authorizationCodeFlowAllowsAssignedApplicationPermissionScope() throws Exception {
        FlowFixture fixture = createFlowFixture();
        ResourceServer resourceServer = resourceServerApplicationService.createResourceServer(
                fixture.client().client().getTenant().getId(), "oauth-payroll-api", "OAuth Payroll API", null);
        ResourcePermission permission = resourceServerApplicationService.createResourcePermission(
                resourceServer.getId(), "payroll.employee.read", "Read employees", null);
        clientApplicationService.updateClient(
                fixture.client().client().getId(),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                resourceServer.getId());
        clientApplicationService.assignResourcePermissionToClient(fixture.client().client().getId(), permission.getId());

        StartedAuthorization startedAuthorization = startAuthorizationRequestExpectingLogin(
                fixture.client().client().getClientId(), "payroll.employee.read", "application-scope-state");
        AuthenticatedSession authenticatedSession =
                login(startedAuthorization.session(), fixture.user().getUsername());
        String code = continueAuthorizationRequestAndExtractCode(
                authenticatedSession.session(), authenticatedSession.authorizationRedirect(), "application-scope-state");
        String accessToken = exchangeCodeForAccessToken(
                fixture.client().client().getClientId(),
                fixture.client().clientSecret(),
                code);

        Jwt jwt = jwtDecoder.decode(accessToken);
        assertThat(jwt.getClaimAsStringList("scope")).containsExactly("payroll.employee.read");

        mockMvc.perform(get("/demo-resource-api/payroll/employees")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].employeeId").value("E-1001"));
    }

    @Test
    void oidcIdTokenAndUserInfoReturnOnlyClaimsAllowedByGrantedIdentityScopes() throws Exception {
        FlowFixture fixture = createFlowFixture();
        fixture.user().setEmail("oidc-user@example.test");
        fixture.user().setEmailVerified(true);
        userApplicationService.updateUser(
                fixture.user().getId(), null, fixture.user().getEmail(), true, null, null, null);
        var group = groupApplicationService.createGroup(
                fixture.user().getTenant().getId(), "oidc-test-group-" + UUID.randomUUID());
        groupApplicationService.addUserToGroup(group.getId(), fixture.user().getId());

        mockMvc.perform(get("/userinfo"))
                .andExpect(status().isUnauthorized());

        TokenResponse openidToken = completeAuthorizationCodeFlow(fixture, "openid", "oidc-openid-state");
        assertThat(openidToken.idToken()).isNotBlank();
        Jwt openidIdToken = jwtDecoder.decode(openidToken.idToken());
        assertThat(openidIdToken.getSubject()).isEqualTo(fixture.user().getId().toString());
        assertThat(openidIdToken.getClaimAsString("preferred_username")).isEqualTo(fixture.user().getUsername());
        assertThat(openidIdToken.getClaimAsString("account_status")).isNull();
        assertThat(openidIdToken.getClaimAsString("email")).isNull();

        mockMvc.perform(get("/userinfo")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + openidToken.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sub").value(fixture.user().getId().toString()))
                .andExpect(jsonPath("$.preferred_username").doesNotExist())
                .andExpect(jsonPath("$.email").doesNotExist())
                .andExpect(jsonPath("$.groups").doesNotExist())
                .andExpect(jsonPath("$.roles").doesNotExist());

        TokenResponse profileToken = completeAuthorizationCodeFlow(
                fixture, "openid profile", "oidc-profile-state");
        Jwt profileIdToken = jwtDecoder.decode(profileToken.idToken());
        assertThat(profileIdToken.getClaimAsString("account_status")).isEqualTo("ACTIVE");
        assertThat(profileIdToken.getClaimAsString("email")).isNull();
        mockMvc.perform(get("/userinfo")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + profileToken.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.preferred_username").value(fixture.user().getUsername()))
                .andExpect(jsonPath("$.display_name").value(fixture.user().getDisplayName()))
                .andExpect(jsonPath("$.tenant_id").value(fixture.user().getTenant().getId().toString()))
                .andExpect(jsonPath("$.tenant_name").value(profileIdToken.getClaimAsString("tenant_name")))
                .andExpect(jsonPath("$.account_status").value("ACTIVE"))
                .andExpect(jsonPath("$.email").doesNotExist());

        TokenResponse allIdentityToken = completeAuthorizationCodeFlow(
                fixture, "openid profile email groups roles", "oidc-all-identity-state");
        Jwt allIdentityIdToken = jwtDecoder.decode(allIdentityToken.idToken());
        assertThat(allIdentityIdToken.getClaimAsString("email")).isEqualTo("oidc-user@example.test");
        assertThat(allIdentityIdToken.getClaimAsBoolean("email_verified")).isTrue();
        assertThat(allIdentityIdToken.getClaims()).doesNotContainKeys("groups", "roles", "effective_roles", "effective_permissions");

        MvcResult userInfoResult = mockMvc.perform(get("/userinfo")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + allIdentityToken.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("oidc-user@example.test"))
                .andExpect(jsonPath("$.email_verified").value(true))
                .andExpect(jsonPath("$.groups[0]").value(group.getName()))
                .andExpect(jsonPath("$.roles").isArray())
                .andExpect(jsonPath("$.effective_roles").isArray())
                .andReturn();
        assertThat(objectMapper.readTree(userInfoResult.getResponse().getContentAsString()).fieldNames())
                .toIterable()
                .doesNotContain(
                        "passwordHash", "password", "credentialsVersion", "totpSecret", "encryptedTotpSecret",
                        "clientSecretHash", "clientSecret", "access_token", "refresh_token", "authorization_code");
    }

    @Test
    void applicationScopeDoesNotCreateOidcIdentityResponse() throws Exception {
        FlowFixture fixture = createFlowFixture();
        ResourceServer resourceServer = resourceServerApplicationService.createResourceServer(
                fixture.client().client().getTenant().getId(), "oidc-separation-api", "OIDC Separation API", null);
        ResourcePermission permission = resourceServerApplicationService.createResourcePermission(
                resourceServer.getId(), "payroll.employee.read", "Read employees", null);
        clientApplicationService.updateClient(
                fixture.client().client().getId(), null, null, null, null, null, null, null, null, resourceServer.getId());
        clientApplicationService.assignResourcePermissionToClient(fixture.client().client().getId(), permission.getId());

        TokenResponse token = completeAuthorizationCodeFlow(
                fixture, "payroll.employee.read", "application-scope-no-identity-state");
        assertThat(token.idToken()).isNull();
        mockMvc.perform(get("/userinfo")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token.accessToken()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("insufficient_scope"));
    }

    @Test
    void authorizationCodeFlowRejectsUnassignedApplicationPermissionScope() throws Exception {
        FlowFixture fixture = createFlowFixture();
        ResourceServer resourceServer = resourceServerApplicationService.createResourceServer(
                fixture.client().client().getTenant().getId(), "oauth-crm-api", "OAuth CRM API", null);
        ResourcePermission permission = resourceServerApplicationService.createResourcePermission(
                resourceServer.getId(), "crm.customer.read", "Read customers", null);
        clientApplicationService.updateClient(
                fixture.client().client().getId(),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                resourceServer.getId());
        clientApplicationService.assignResourcePermissionToClient(fixture.client().client().getId(), permission.getId());

        String redirectUrl = startAuthorizationRequestExpectingOAuth2Error(
                fixture.client().client().getClientId(), "crm.customer.write", "unassigned-application-scope-state");

        var queryParams = UriComponentsBuilder.fromUriString(redirectUrl).build().getQueryParams();
        assertThat(queryParams.getFirst("error")).isEqualTo("invalid_scope");
        assertThat(queryParams.getFirst("code")).isNull();
    }

    @Test
    void authorizationCodeClientReceivesRefreshTokenAndCanRefreshAccessToken() throws Exception {
        FlowFixture fixture = createFlowFixture();

        TokenResponse initialToken = completeAuthorizationCodeFlow(
                fixture, "iam.read iam.write", "refresh-token-state");
        assertThat(initialToken.accessToken()).isNotBlank();
        assertThat(initialToken.refreshToken()).isNotBlank();

        TokenResponse refreshedToken = refreshAccessToken(
                fixture.client().client().getClientId(),
                fixture.client().clientSecret(),
                initialToken.refreshToken(),
                "iam.read");

        assertThat(refreshedToken.accessToken()).isNotBlank();
        assertThat(refreshedToken.refreshToken()).isNotBlank();
        assertThat(jwtDecoder.decode(refreshedToken.accessToken()).getClaimAsStringList("scope"))
                .containsExactly("iam.read");
        assertThat(auditLogRepository.findByAction("OAUTH2_TOKEN_REFRESHED")).isNotEmpty();
    }

    @Test
    void refreshTokenCannotExpandBeyondOriginalOrAllowedScopesAndCanBeRevoked() throws Exception {
        FlowFixture fixture = createFlowFixture();
        ResourceServer resourceServer = resourceServerApplicationService.createResourceServer(
                fixture.client().client().getTenant().getId(), "oauth-refresh-payroll-api", "OAuth Refresh Payroll API", null);
        ResourcePermission employeeRead = resourceServerApplicationService.createResourcePermission(
                resourceServer.getId(), "payroll.employee.read", "Read employees", null);
        ResourcePermission salaryRead = resourceServerApplicationService.createResourcePermission(
                resourceServer.getId(), "payroll.salary.read", "Read salaries", null);
        clientApplicationService.updateClient(
                fixture.client().client().getId(),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                resourceServer.getId());
        clientApplicationService.assignResourcePermissionToClient(fixture.client().client().getId(), employeeRead.getId());
        clientApplicationService.assignResourcePermissionToClient(fixture.client().client().getId(), salaryRead.getId());

        TokenResponse initialToken = completeAuthorizationCodeFlow(
                fixture, "payroll.employee.read", "refresh-application-scope-state");

        mockMvc.perform(post("/oauth2/token")
                        .with(httpBasic(fixture.client().client().getClientId(), fixture.client().clientSecret()))
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "refresh_token")
                        .param("refresh_token", initialToken.refreshToken())
                        .param("scope", "payroll.salary.read"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("invalid_scope"));

        mockMvc.perform(post("/oauth2/revoke")
                        .with(httpBasic(fixture.client().client().getClientId(), fixture.client().clientSecret()))
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("token", initialToken.refreshToken())
                        .param("token_type_hint", "refresh_token"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/oauth2/token")
                        .with(httpBasic(fixture.client().client().getClientId(), fixture.client().clientSecret()))
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "refresh_token")
                        .param("refresh_token", initialToken.refreshToken()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("invalid_grant"));
        assertThat(auditLogRepository.findByAction("OAUTH2_TOKEN_REVOKED")).isNotEmpty();
    }

    @Test
    void consentCanBeListedAndRevokedSafely() throws Exception {
        auditLogRepository.deleteAll();
        FlowFixture fixture = createFlowFixture(true);

        StartedAuthorization startedAuthorization = startAuthorizationRequestExpectingLogin(
                fixture.client().client().getClientId(), "iam.read iam.write", "consent-management-state");
        AuthenticatedSession authenticatedSession =
                login(startedAuthorization.session(), fixture.user().getUsername());
        String consentUrl = expectConsentRedirect(
                authenticatedSession.session(), authenticatedSession.authorizationRedirect());
        String approvalRedirect = submitConsent(
                authenticatedSession.session(), consentUrl, fixture.client().client().getClientId(), true);
        String code = extractCode(approvalRedirect, "consent-management-state");
        TokenResponse token = exchangeCodeForTokenResponse(
                fixture.client().client().getClientId(),
                fixture.client().clientSecret(),
                code);

        MvcResult listResult = mockMvc.perform(get("/api/oauth2/consents")
                        .param("userId", fixture.user().getId().toString())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].clientId").value(fixture.client().client().getClientId()))
                .andExpect(jsonPath("$[0].clientName").value("OAuth Flow Demo Client"))
                .andExpect(jsonPath("$[0].scopes").isArray())
                .andExpect(jsonPath("$[0].accessToken").doesNotExist())
                .andExpect(jsonPath("$[0].refreshToken").doesNotExist())
                .andExpect(jsonPath("$[0].clientSecret").doesNotExist())
                .andReturn();
        assertThat(listResult.getResponse().getContentAsString())
                .doesNotContain(token.accessToken(), token.refreshToken(), fixture.client().clientSecret());

        mockMvc.perform(get("/api/oauth2/consents/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].clientId").value(fixture.client().client().getClientId()));

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .delete("/api/oauth2/consents/{clientId}", fixture.client().client().getClientId())
                        .param("userId", fixture.user().getId().toString())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token.accessToken()))
                .andExpect(status().isNoContent());

        String secondConsentUrl = expectConsentRedirect(
                authenticatedSession.session(),
                authorizationRequest(fixture.client().client().getClientId(), "iam.read", "consent-required-again"));
        assertThat(secondConsentUrl).contains("/oauth2/consent?");
        assertThat(auditLogRepository.findByAction("OAUTH2_CONSENT_REVOKED")).isNotEmpty();
        assertThat(auditLogRepository.findAll()).allSatisfy(auditLog -> {
            assertThat(auditLog.getAction()).doesNotContain(token.accessToken(), token.refreshToken());
            assertThat(auditLog.getResourceType()).doesNotContain(fixture.client().clientSecret());
        });
    }

    @Test
    void authorizationCodeFlowIssuesPayrollScopesThatProtectDemoResourceApi() throws Exception {
        FlowFixture fixture = createFlowFixture();
        ResourceServer resourceServer = resourceServerApplicationService.createResourceServer(
                fixture.client().client().getTenant().getId(), "oauth-payroll-api-full", "OAuth Payroll API Full", null);
        ResourcePermission employeeRead = resourceServerApplicationService.createResourcePermission(
                resourceServer.getId(), "payroll.employee.read", "Read employees", null);
        ResourcePermission salaryRead = resourceServerApplicationService.createResourcePermission(
                resourceServer.getId(), "payroll.salary.read", "Read salaries", null);
        ResourcePermission salaryWrite = resourceServerApplicationService.createResourcePermission(
                resourceServer.getId(), "payroll.salary.write", "Write salaries", null);
        clientApplicationService.updateClient(
                fixture.client().client().getId(),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                resourceServer.getId());
        clientApplicationService.assignResourcePermissionToClient(fixture.client().client().getId(), employeeRead.getId());
        clientApplicationService.assignResourcePermissionToClient(fixture.client().client().getId(), salaryRead.getId());
        clientApplicationService.assignResourcePermissionToClient(fixture.client().client().getId(), salaryWrite.getId());

        StartedAuthorization startedAuthorization = startAuthorizationRequestExpectingLogin(
                fixture.client().client().getClientId(), "payroll.employee.read", "payroll-resource-state");
        AuthenticatedSession authenticatedSession =
                login(startedAuthorization.session(), fixture.user().getUsername());
        String employeeReadToken = authorizeAndExchange(
                authenticatedSession.session(),
                authenticatedSession.authorizationRedirect(),
                fixture.client(),
                "payroll-resource-state");
        assertThat(jwtDecoder.decode(employeeReadToken).getClaimAsStringList("scope"))
                .containsExactly("payroll.employee.read");
        mockMvc.perform(get("/demo-resource-api/payroll/employees")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + employeeReadToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].salaryAmount").doesNotExist());
        mockMvc.perform(get("/demo-resource-api/payroll/salaries")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + employeeReadToken))
                .andExpect(status().isForbidden());

        String salaryReadToken = authorizeAuthenticatedSessionAndExchange(
                authenticatedSession.session(),
                fixture.client(),
                "payroll.salary.read",
                "payroll-salary-read-state");
        mockMvc.perform(get("/demo-resource-api/payroll/salaries")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + salaryReadToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].salaryAmount").value(132000.00));
        mockMvc.perform(post("/demo-resource-api/payroll/salaries")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + salaryReadToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());

        String salaryWriteToken = authorizeAuthenticatedSessionAndExchange(
                authenticatedSession.session(),
                fixture.client(),
                "payroll.salary.write",
                "payroll-salary-write-state");
        mockMvc.perform(post("/demo-resource-api/payroll/salaries")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + salaryWriteToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("accepted"));
    }

    private FlowFixture createFlowFixture() {
        return createFlowFixture(false);
    }

    private FlowFixture createFlowFixture(boolean requireConsent) {
        String uniqueSuffix = UUID.randomUUID().toString();
        Tenant tenant = tenantApplicationService.createTenant("OAuth2 Flow Tenant " + uniqueSuffix);
        User user = userApplicationService.createUser(
                tenant.getId(), "oauth-flow-user-" + uniqueSuffix, "OAuth Flow User");
        user = userApplicationService.setInitialPassword(user.getId(), RAW_PASSWORD);
        Role tenantAdmin = roleRepository.findByTenantIdAndName(
                        tenant.getId(), SystemPermissionCatalogService.TENANT_ADMIN_ROLE_NAME)
                .orElseThrow();
        user = userApplicationService.assignRoleToUser(user.getId(), tenantAdmin.getId());

        ClientSecretResult client = clientApplicationService.createClientWithSecret(
                tenant.getId(),
                "oauth-flow-client-" + uniqueSuffix,
                "OAuth Flow Demo Client",
                ClientType.CONFIDENTIAL,
                false,
                requireConsent,
                Set.of(REDIRECT_URI),
                Set.of("authorization_code", "refresh_token"),
                Set.of("iam.read", "iam.write", "openid", "profile", "email", "groups", "roles"),
                Set.of("client_secret_basic"));

        return new FlowFixture(user, client);
    }

    private TokenResponse completeAuthorizationCodeFlow(
            FlowFixture fixture,
            String scope,
            String state) throws Exception {
        StartedAuthorization startedAuthorization = startAuthorizationRequestExpectingLogin(
                fixture.client().client().getClientId(), scope, state);
        AuthenticatedSession authenticatedSession =
                login(startedAuthorization.session(), fixture.user().getUsername());
        String code = continueAuthorizationRequestAndExtractCode(
                authenticatedSession.session(), authenticatedSession.authorizationRedirect(), state);
        return exchangeCodeForTokenResponse(
                fixture.client().client().getClientId(),
                fixture.client().clientSecret(),
                code);
    }

    private StartedAuthorization startAuthorizationRequestExpectingLogin(
            String clientId,
            String scope,
            String state) throws Exception {
        MvcResult result = mockMvc.perform(get(authorizationRequest(clientId, scope, state))
                        .accept(MediaType.TEXT_HTML))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("http://localhost/login"))
                .andReturn();

        return new StartedAuthorization((MockHttpSession) result.getRequest().getSession(false));
    }

    private String startAuthorizationRequestExpectingOAuth2Error(
            String clientId,
            String scope,
            String state) throws Exception {
        MvcResult result = mockMvc.perform(get(authorizationRequest(clientId, scope, state))
                        .accept(MediaType.TEXT_HTML))
                .andExpect(status().isFound())
                .andExpect(header().string(HttpHeaders.LOCATION, startsWith(REDIRECT_URI + "?")))
                .andReturn();
        return result.getResponse().getRedirectedUrl();
    }

    private AuthenticatedSession login(MockHttpSession session, String username) throws Exception {
        MvcResult result = mockMvc.perform(post("/login")
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("username", username)
                        .param("password", RAW_PASSWORD))
                .andExpect(status().isFound())
                .andExpect(header().string(HttpHeaders.LOCATION, startsWith("http://localhost/oauth2/authorize")))
                .andReturn();

        return new AuthenticatedSession(session, result.getResponse().getRedirectedUrl());
    }

    private String continueAuthorizationRequestAndExtractCode(
            MockHttpSession session,
            String loginSuccessRedirect,
            String expectedState) throws Exception {
        return extractCode(authorize(session, loginSuccessRedirect), expectedState);
    }

    private String authorizeAndExchange(
            MockHttpSession session,
            String authorizationUrl,
            ClientSecretResult client,
            String expectedState) throws Exception {
        String code = extractCode(authorize(session, authorizationUrl), expectedState);
        return exchangeCodeForAccessToken(client.client().getClientId(), client.clientSecret(), code);
    }

    private String authorizeAuthenticatedSessionAndExtractCode(
            MockHttpSession session,
            String clientId,
            String scope,
            String expectedState) throws Exception {
        return extractCode(authorize(session, authorizationRequest(clientId, scope, expectedState)), expectedState);
    }

    private String authorizeAuthenticatedSessionAndExchange(
            MockHttpSession session,
            ClientSecretResult client,
            String scope,
            String expectedState) throws Exception {
        String code = authorizeAuthenticatedSessionAndExtractCode(
                session, client.client().getClientId(), scope, expectedState);
        return exchangeCodeForAccessToken(client.client().getClientId(), client.clientSecret(), code);
    }

    private String authorize(MockHttpSession session, String authorizationUrl) throws Exception {
        MvcResult result = mockMvc.perform(get(URI.create(authorizationUrl)).session(session))
                .andExpect(status().isFound())
                .andExpect(header().string(HttpHeaders.LOCATION, startsWith(REDIRECT_URI + "?")))
                .andReturn();

        return result.getResponse().getRedirectedUrl();
    }

    private String expectConsentRedirect(MockHttpSession session, String authorizationUrl) throws Exception {
        MvcResult result = mockMvc.perform(get(URI.create(authorizationUrl)).session(session))
                .andExpect(status().isFound())
                .andExpect(header().string(HttpHeaders.LOCATION, startsWith("http://localhost/oauth2/consent?")))
                .andReturn();
        return result.getResponse().getRedirectedUrl();
    }

    private String submitConsent(
            MockHttpSession session,
            String consentUrl,
            String clientId,
            boolean approve) throws Exception {
        Map<String, String> params = decodedQueryParams(consentUrl);
        var requestBuilder = post("/oauth2/authorize")
                .session(session)
                .with(csrf())
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("client_id", clientId)
                .param("state", params.get("state"));
        if (approve) {
            for (String scope : params.get("scope").split(" ")) {
                requestBuilder.param("scope", scope);
            }
        }

        MvcResult result = mockMvc.perform(requestBuilder)
                .andExpect(status().isFound())
                .andExpect(header().string(HttpHeaders.LOCATION, startsWith(REDIRECT_URI + "?")))
                .andReturn();
        return result.getResponse().getRedirectedUrl();
    }

    private Map<String, String> decodedQueryParams(String url) {
        String rawQuery = URI.create(url).getRawQuery();
        return Arrays.stream(rawQuery.split("&"))
                .map(part -> part.split("=", 2))
                .collect(Collectors.toMap(
                        part -> URLDecoder.decode(part[0], StandardCharsets.UTF_8),
                        part -> part.length > 1 ? URLDecoder.decode(part[1], StandardCharsets.UTF_8) : ""));
    }

    private String exchangeCodeForAccessToken(String clientId, String clientSecret, String code) throws Exception {
        return exchangeCodeForTokenResponse(clientId, clientSecret, code).accessToken();
    }

    private TokenResponse exchangeCodeForTokenResponse(String clientId, String clientSecret, String code) throws Exception {
        MvcResult result = mockMvc.perform(post("/oauth2/token")
                        .with(httpBasic(clientId, clientSecret))
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "authorization_code")
                        .param("code", code)
                        .param("redirect_uri", REDIRECT_URI))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.access_token").isNotEmpty())
                .andExpect(jsonPath("$.token_type").value("Bearer"))
                .andExpect(jsonPath("$.expires_in").isNumber())
                .andReturn();

        JsonNode tokenResponse = objectMapper.readTree(result.getResponse().getContentAsString());
        return new TokenResponse(
                tokenResponse.get("access_token").asText(),
                tokenResponse.has("refresh_token") ? tokenResponse.get("refresh_token").asText() : null,
                tokenResponse.has("id_token") ? tokenResponse.get("id_token").asText() : null);
    }

    private TokenResponse refreshAccessToken(
            String clientId,
            String clientSecret,
            String refreshToken,
            String scope) throws Exception {
        var request = post("/oauth2/token")
                .with(httpBasic(clientId, clientSecret))
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("grant_type", "refresh_token")
                .param("refresh_token", refreshToken);
        if (scope != null) {
            request.param("scope", scope);
        }
        MvcResult result = mockMvc.perform(request)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.access_token").isNotEmpty())
                .andExpect(jsonPath("$.token_type").value("Bearer"))
                .andReturn();

        JsonNode tokenResponse = objectMapper.readTree(result.getResponse().getContentAsString());
        return new TokenResponse(
                tokenResponse.get("access_token").asText(),
                tokenResponse.has("refresh_token") ? tokenResponse.get("refresh_token").asText() : null,
                tokenResponse.has("id_token") ? tokenResponse.get("id_token").asText() : null);
    }

    private String extractCode(String redirectUrl, String expectedState) {
        var queryParams = UriComponentsBuilder.fromUriString(redirectUrl).build().getQueryParams();

        assertThat(queryParams.getFirst("error")).isNull();
        assertThat(queryParams.getFirst("state")).isEqualTo(expectedState);
        assertThat(queryParams.getFirst("code")).isNotBlank();

        return queryParams.getFirst("code");
    }

    private String authorizationRequest(String clientId, String scope, String state) {
        return UriComponentsBuilder.fromPath("/oauth2/authorize")
                .queryParam("response_type", "code")
                .queryParam("client_id", clientId)
                .queryParam("redirect_uri", REDIRECT_URI)
                .queryParam("scope", scope)
                .queryParam("state", state)
                .build()
                .toUriString();
    }

    private record FlowFixture(User user, ClientSecretResult client) {
    }

    private record StartedAuthorization(MockHttpSession session) {
    }

    private record AuthenticatedSession(MockHttpSession session, String authorizationRedirect) {
    }

    private record TokenResponse(String accessToken, String refreshToken, String idToken) {
    }
}

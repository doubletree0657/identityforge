package io.github.doubletree.iam.oauth.infrastructure;

import static org.hamcrest.Matchers.startsWith;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.doubletree.iam.directory.domain.AccountStatus;
import io.github.doubletree.iam.applications.domain.Client;
import io.github.doubletree.iam.directory.domain.PasswordCredential;
import io.github.doubletree.iam.directory.domain.Tenant;
import io.github.doubletree.iam.directory.domain.User;
import io.github.doubletree.iam.authentication.application.MfaApplicationService;
import io.github.doubletree.iam.authentication.api.MfaEnrollmentResult;
import io.github.doubletree.iam.audit.infrastructure.AuditLogRepository;
import io.github.doubletree.iam.applications.infrastructure.persistence.ClientRepository;
import io.github.doubletree.iam.directory.infrastructure.persistence.TenantRepository;
import io.github.doubletree.iam.directory.infrastructure.persistence.UserRepository;
import java.lang.reflect.Method;
import java.time.Instant;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(properties = "spring.application.name=identityforge")
@AutoConfigureMockMvc
@Testcontainers
class HttpLoginFlowTests {

    private static final String PASSWORD = "correct-password-123";

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ClientRepository clientRepository;

    @Autowired
    private MfaApplicationService mfaApplicationService;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @DynamicPropertySource
    static void registerDataSourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", postgres::getDriverClassName);
    }

    @BeforeEach
    void seedDevelopmentClient() {
        auditLogRepository.deleteAll();
        if (!clientRepository.findAllByClientId("identityforge-dev").isEmpty()) {
            return;
        }

        Tenant tenant = tenantRepository.save(Tenant.create("HTTP Login Flow Client Tenant"));
        Client client = Client.create(tenant, "identityforge-dev", "IdentityForge Dev");
        client.setClientSecretHash(passwordEncoder.encode("secret"));
        client.setRequirePkce(false);
        client.setRequireConsent(false);
        client.setRedirectUris(Set.of("http://127.0.0.1:8080/login/oauth2/code/identityforge-dev"));
        client.setGrantTypes(Set.of("client_credentials", "authorization_code"));
        client.setScopes(Set.of("iam.read", "iam.write"));
        client.setAuthenticationMethods(Set.of("client_secret_basic"));
        clientRepository.saveAndFlush(client);
    }

    @Test
    void customLoginPageIsAvailable() throws Exception {
        mockMvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(result -> assertThat(result.getResponse().getContentAsString())
                        .contains("IAM Sign In")
                        .contains("username")
                        .contains("password"));
    }

    @Test
    void activeUserCanLoginThroughHttpFormAndCreateSession() throws Exception {
        createUser("http-active-user", PASSWORD, AccountStatus.ACTIVE);

        MvcResult result = mockMvc.perform(formLogin().user(loginIdentifier("http-active-user")).password(PASSWORD))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/"))
                .andExpect(authenticated().withUsername("http-active-user"))
                .andReturn();

        assertThat(result.getRequest().getSession(false)).isNotNull();
    }

    @Test
    void wrongPasswordCannotLoginThroughHttpForm() throws Exception {
        createUser("http-wrong-password-user", PASSWORD, AccountStatus.ACTIVE);

        assertLoginFails("http-wrong-password-user", "wrong-password");

        assertThat(auditLogRepository.findByAction("USER_AUTHENTICATION_FAILED"))
                .singleElement()
                .satisfies(auditLog -> {
                    assertThat(auditLog.getResult().name()).isEqualTo("FAILURE");
                    assertAuditLogDoesNotContainSecrets(auditLog);
                });
    }

    @Test
    void missingUserCannotLoginThroughHttpForm() throws Exception {
        assertLoginFails("http-missing-user", PASSWORD);
    }

    @Test
    void userWithoutPasswordCredentialCannotLoginThroughHttpForm() throws Exception {
        createUserWithoutPassword("http-no-password-user", AccountStatus.ACTIVE);

        assertLoginFails("http-no-password-user", PASSWORD);
    }

    @ParameterizedTest
    @EnumSource(value = AccountStatus.class, names = {"DISABLED", "LOCKED", "PENDING"})
    void inactiveUsersCannotLoginThroughHttpForm(AccountStatus accountStatus) throws Exception {
        String username = "http-" + accountStatus.name().toLowerCase() + "-user";
        createUser(username, PASSWORD, accountStatus);

        assertLoginFails(username, PASSWORD);
        assertThat(auditLogRepository.findByAction("USER_AUTHENTICATION_BLOCKED"))
                .singleElement()
                .satisfies(auditLog -> {
                    assertThat(auditLog.getResult().name()).isEqualTo("FAILURE");
                    assertAuditLogDoesNotContainSecrets(auditLog);
                });
    }

    @Test
    void userWithVerifiedTotpCredentialMustCompleteMfaChallenge() throws Exception {
        User user = createUser("http-mfa-user", PASSWORD, AccountStatus.ACTIVE);
        MfaEnrollmentResult enrollment = mfaApplicationService.enrollTotp(user.getId());
        String code = generateTotpCode(enrollment.secret());
        assertThat(mfaApplicationService.verifyTotp(user.getId(), code).verified()).isTrue();

        MvcResult passwordResult = mockMvc.perform(formLogin().user(loginIdentifier(user.getUsername())).password(PASSWORD))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/login/mfa"))
                .andReturn();

        MockHttpSession session = (MockHttpSession) passwordResult.getRequest().getSession(false);

        mockMvc.perform(post("/login/mfa")
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("code", "000000"))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/login/mfa?error"))
                .andExpect(unauthenticated());

        mockMvc.perform(post("/login/mfa")
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("code", generateTotpCode(enrollment.secret())))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/"))
                .andExpect(authenticated().withUsername(user.getUsername()));

        assertThat(auditLogRepository.findByAction("MFA_CHALLENGE_FAILED"))
                .singleElement()
                .satisfies(this::assertAuditLogDoesNotContainSecrets);
        assertThat(auditLogRepository.findByAction("MFA_CHALLENGE_SUCCEEDED"))
                .singleElement()
                .satisfies(this::assertAuditLogDoesNotContainSecrets);
    }

    @Test
    void userCanCompleteMfaChallengeWithARecoveryCodeOnlyOnce() throws Exception {
        User user = createUser("http-recovery-user", PASSWORD, AccountStatus.ACTIVE);
        MfaEnrollmentResult enrollment = mfaApplicationService.enrollTotp(user.getId());
        String recoveryCode = mfaApplicationService
                .verifyTotp(user.getId(), generateTotpCode(enrollment.secret()))
                .recoveryCodes()
                .getFirst();

        MockHttpSession firstSession = pendingMfaSession(user);
        mockMvc.perform(post("/login/mfa")
                        .session(firstSession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("code", recoveryCode))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/"))
                .andExpect(authenticated().withUsername(user.getUsername()));

        MockHttpSession secondSession = pendingMfaSession(user);
        mockMvc.perform(post("/login/mfa")
                        .session(secondSession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("code", recoveryCode))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/login/mfa?error"))
                .andExpect(unauthenticated());

        assertThat(auditLogRepository.findByAction("MFA_RECOVERY_CODE_USED")).hasSize(1);
        auditLogRepository.findByAction("MFA_RECOVERY_CODE_USED").forEach(this::assertAuditLogDoesNotContainSecrets);
    }

    @Test
    void healthEndpointRemainsPublic() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void apiEndpointsIgnoreBrowserSessionsAndRequireBearerTokens() throws Exception {
        createUser("http-api-boundary-user", PASSWORD, AccountStatus.ACTIVE);
        MockHttpSession session = loginSession("http-api-boundary-user", PASSWORD);

        mockMvc.perform(post("/api/tenants")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Should Not Be Created By Login Session"}
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void apiEndpointsWithoutJwtStillReturnBearerUnauthorized() throws Exception {
        mockMvc.perform(post("/api/tenants")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"No Token Tenant"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"));
    }

    @Test
    void authorizationEndpointRedirectsBrowserUserToLoginWhenAuthenticationIsRequired() throws Exception {
        mockMvc.perform(get(authorizationRequest("login-required-state"))
                        .accept(MediaType.TEXT_HTML))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("http://localhost/login"));
    }

    @Test
    void loggedInUserCanReachAuthorizationEndpointForFutureAuthorizationCodeFlowReadiness() throws Exception {
        createUser("http-authorize-user", PASSWORD, AccountStatus.ACTIVE);
        MockHttpSession session = loginSession("http-authorize-user", PASSWORD);

        mockMvc.perform(get(authorizationRequest("authenticated-state"))
                        .session(session))
                .andExpect(status().isFound())
                .andExpect(header().string(HttpHeaders.LOCATION,
                        startsWith("http://127.0.0.1:8080/login/oauth2/code/identityforge-dev?")));
    }

    @Test
    void securityStateChangeTerminatesAnExistingBrowserSession() throws Exception {
        User user = createUser("http-stale-session-user", PASSWORD, AccountStatus.ACTIVE);
        MockHttpSession session = loginSession(user.getUsername(), PASSWORD);
        PasswordCredential credential = user.getPasswordCredential();
        credential.setCredentialsVersion(credential.getCredentialsVersion() + 1);
        userRepository.saveAndFlush(user);

        mockMvc.perform(get(authorizationRequest("stale-session-state"))
                .session(session)
                        .accept(MediaType.TEXT_HTML))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/login?reason=session"));

        assertThat(session.isInvalid()).isTrue();
        assertThat(auditLogRepository.findByAction("USER_SESSION_REJECTED"))
                .singleElement()
                .satisfies(audit -> assertThat(audit.getReasonCode()).isEqualTo("SECURITY_STATE_CHANGED"));
    }

    private String authorizationRequest(String state) {
        return "/oauth2/authorize"
                + "?response_type=code"
                + "&client_id=identityforge-dev"
                + "&redirect_uri=http://127.0.0.1:8080/login/oauth2/code/identityforge-dev"
                + "&scope=iam.read"
                + "&state=" + state;
    }

    private MockHttpSession loginSession(String username, String password) throws Exception {
        MvcResult result = mockMvc.perform(formLogin().user(loginIdentifier(username)).password(password))
                .andExpect(status().isFound())
                .andExpect(authenticated().withUsername(username))
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    private MockHttpSession pendingMfaSession(User user) throws Exception {
        MvcResult result = mockMvc.perform(formLogin().user(loginIdentifier(user.getUsername())).password(PASSWORD))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/login/mfa"))
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    private void assertLoginFails(String username, String password) throws Exception {
        mockMvc.perform(formLogin().user(loginIdentifier(username)).password(password))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/login?error=credentials"))
                .andExpect(unauthenticated());
    }

    private User createUser(String username, String rawPassword, AccountStatus accountStatus) {
        User user = createUserWithoutPassword(username, accountStatus);
        PasswordCredential credential = user.ensurePasswordCredential();
        credential.setPasswordHash(passwordEncoder.encode(rawPassword));
        credential.setPasswordResetRequired(false);
        return userRepository.save(user);
    }

    private User createUserWithoutPassword(String username, AccountStatus accountStatus) {
        Tenant tenant = tenantRepository.save(Tenant.create(username + "-tenant"));
        User user = User.create(tenant, username, username + " Display");
        user.setAccountStatus(accountStatus);
        return userRepository.save(user);
    }

    private String loginIdentifier(String username) {
        return username + "-tenant/" + username;
    }

    private String generateTotpCode(String secret) throws Exception {
        Method method = MfaApplicationService.class.getDeclaredMethod("generateTotpCode", String.class, Instant.class);
        method.setAccessible(true);
        return (String) method.invoke(mfaApplicationService, secret, Instant.now());
    }

    private void assertAuditLogDoesNotContainSecrets(Object auditLog) {
        String rendered = auditLog.toString();
        assertThat(rendered)
                .doesNotContain("correct-password-123")
                .doesNotContain("000000")
                .doesNotContain("secret")
                .doesNotContain("ciphertext")
                .doesNotContain("access_token")
                .doesNotContain("authorization_code");
    }
}

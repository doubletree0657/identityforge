package io.github.doubletree.iam.platform.authorization;

import static org.hamcrest.Matchers.startsWith;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.doubletree.iam.platform.domain.AccountStatus;
import io.github.doubletree.iam.platform.domain.Client;
import io.github.doubletree.iam.platform.domain.PasswordCredential;
import io.github.doubletree.iam.platform.domain.Tenant;
import io.github.doubletree.iam.platform.domain.User;
import io.github.doubletree.iam.platform.repository.ClientRepository;
import io.github.doubletree.iam.platform.repository.TenantRepository;
import io.github.doubletree.iam.platform.repository.UserRepository;
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

@SpringBootTest(properties = "spring.application.name=international-iam-platform")
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

    @DynamicPropertySource
    static void registerDataSourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", postgres::getDriverClassName);
    }

    @BeforeEach
    void seedDevelopmentClient() {
        if (!clientRepository.findAllByClientId("international-iam-dev").isEmpty()) {
            return;
        }

        Tenant tenant = tenantRepository.save(Tenant.create("HTTP Login Flow Client Tenant"));
        Client client = Client.create(tenant, "international-iam-dev", "International IAM Dev");
        client.setClientSecretHash(passwordEncoder.encode("secret"));
        client.setRequirePkce(false);
        client.setRequireConsent(false);
        client.setRedirectUris(Set.of("http://127.0.0.1:8080/login/oauth2/code/international-iam-dev"));
        client.setGrantTypes(Set.of("client_credentials", "authorization_code"));
        client.setScopes(Set.of("iam.read", "iam.write"));
        client.setAuthenticationMethods(Set.of("client_secret_basic"));
        clientRepository.saveAndFlush(client);
    }

    @Test
    void activeUserCanLoginThroughHttpFormAndCreateSession() throws Exception {
        createUser("http-active-user", PASSWORD, AccountStatus.ACTIVE);

        MvcResult result = mockMvc.perform(formLogin().user("http-active-user").password(PASSWORD))
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
    }

    @Test
    void healthEndpointRemainsPublic() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void apiEndpointsRemainProtectedByJwtScopesAfterLogin() throws Exception {
        createUser("http-api-boundary-user", PASSWORD, AccountStatus.ACTIVE);
        MockHttpSession session = loginSession("http-api-boundary-user", PASSWORD);

        mockMvc.perform(post("/api/tenants")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Should Not Be Created By Login Session"}
                                """))
                .andExpect(status().isForbidden());
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
                        startsWith("http://127.0.0.1:8080/login/oauth2/code/international-iam-dev?")));
    }

    private String authorizationRequest(String state) {
        return "/oauth2/authorize"
                + "?response_type=code"
                + "&client_id=international-iam-dev"
                + "&redirect_uri=http://127.0.0.1:8080/login/oauth2/code/international-iam-dev"
                + "&scope=iam.read"
                + "&state=" + state;
    }

    private MockHttpSession loginSession(String username, String password) throws Exception {
        MvcResult result = mockMvc.perform(formLogin().user(username).password(password))
                .andExpect(status().isFound())
                .andExpect(authenticated().withUsername(username))
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    private void assertLoginFails(String username, String password) throws Exception {
        mockMvc.perform(formLogin().user(username).password(password))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/login?error"))
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
}

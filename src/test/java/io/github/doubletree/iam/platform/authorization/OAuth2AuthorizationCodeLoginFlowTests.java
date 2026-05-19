package io.github.doubletree.iam.platform.authorization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.doubletree.iam.platform.application.result.ClientSecretResult;
import io.github.doubletree.iam.platform.application.service.ClientApplicationService;
import io.github.doubletree.iam.platform.application.service.TenantApplicationService;
import io.github.doubletree.iam.platform.application.service.UserApplicationService;
import io.github.doubletree.iam.platform.domain.ClientType;
import io.github.doubletree.iam.platform.domain.Tenant;
import io.github.doubletree.iam.platform.domain.User;
import java.net.URI;
import java.util.Set;
import java.util.UUID;
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

@SpringBootTest(properties = "spring.application.name=international-iam-platform")
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

    private FlowFixture createFlowFixture() {
        String uniqueSuffix = UUID.randomUUID().toString();
        Tenant tenant = tenantApplicationService.createTenant("OAuth2 Flow Tenant " + uniqueSuffix);
        User user = userApplicationService.createUser(
                tenant.getId(), "oauth-flow-user-" + uniqueSuffix, "OAuth Flow User");
        user = userApplicationService.setInitialPassword(user.getId(), RAW_PASSWORD);

        ClientSecretResult client = clientApplicationService.createClientWithSecret(
                tenant.getId(),
                "oauth-flow-client-" + uniqueSuffix,
                "OAuth Flow Demo Client",
                ClientType.CONFIDENTIAL,
                false,
                false,
                Set.of(REDIRECT_URI),
                Set.of("authorization_code"),
                Set.of("iam.read", "iam.write"),
                Set.of("client_secret_basic"));

        return new FlowFixture(user, client);
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

    private String authorizeAuthenticatedSessionAndExtractCode(
            MockHttpSession session,
            String clientId,
            String scope,
            String expectedState) throws Exception {
        return extractCode(authorize(session, authorizationRequest(clientId, scope, expectedState)), expectedState);
    }

    private String authorize(MockHttpSession session, String authorizationUrl) throws Exception {
        MvcResult result = mockMvc.perform(get(URI.create(authorizationUrl)).session(session))
                .andExpect(status().isFound())
                .andExpect(header().string(HttpHeaders.LOCATION, startsWith(REDIRECT_URI + "?")))
                .andReturn();

        return result.getResponse().getRedirectedUrl();
    }

    private String exchangeCodeForAccessToken(String clientId, String clientSecret, String code) throws Exception {
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
        return tokenResponse.get("access_token").asText();
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
}

package io.github.doubletree.iam.oauth.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
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
@ActiveProfiles("dev")
@Testcontainers
class AdminConsolePublicClientFlowTests {

    private static final String CLIENT_ID = "identityforge-console";
    private static final String REDIRECT_URI = "http://localhost:5173/oauth2/callback";
    private static final String FRONTEND_ORIGIN = "http://localhost:5173";
    private static final String CODE_VERIFIER =
            "identityforge-admin-console-regression-code-verifier-1234567890";
    private static final Pattern CSRF_INPUT = Pattern.compile(
            "name=\"_csrf\" value=\"([^\"]+)\"");

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @DynamicPropertySource
    static void registerDataSourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", postgres::getDriverClassName);
    }

    @Test
    void bootstrapPublicClientCompletesPkceFlowAndLoadsDashboardIdentity() throws Exception {
        String state = "admin-console-regression-state";
        String authorizationRequest = UriComponentsBuilder.fromPath("/oauth2/authorize")
                .queryParam("response_type", "code")
                .queryParam("client_id", CLIENT_ID)
                .queryParam("redirect_uri", REDIRECT_URI)
                .queryParam("scope", "openid profile iam.read iam.write")
                .queryParam("state", state)
                .queryParam("code_challenge", codeChallenge(CODE_VERIFIER))
                .queryParam("code_challenge_method", "S256")
                .build()
                .toUriString();

        MvcResult authorizationStart = mockMvc.perform(get(authorizationRequest).accept(MediaType.TEXT_HTML))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("http://localhost/login"))
                .andReturn();
        MockHttpSession session = (MockHttpSession) authorizationStart.getRequest().getSession(false);
        assertThat(session).isNotNull();

        MvcResult loginPage = mockMvc.perform(get("/login").session(session))
                .andExpect(status().isOk())
                .andReturn();
        String loginHtml = loginPage.getResponse().getContentAsString();
        assertThat(loginHtml)
                .contains("Sign in to IdentityForge")
                .doesNotContain("Whitelabel Error Page");
        String csrfToken = renderedCsrfToken(loginHtml);

        MvcResult login = mockMvc.perform(post("/login")
                        .session(session)
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("_csrf", csrfToken)
                        .param("username", "development/admin")
                        .param("password", "admin123456"))
                .andExpect(status().isFound())
                .andExpect(header().string(HttpHeaders.LOCATION, startsWith("http://localhost/oauth2/authorize")))
                .andReturn();

        MvcResult authorizationComplete = mockMvc.perform(get(URI.create(login.getResponse().getRedirectedUrl()))
                        .session(session)
                        .accept(MediaType.TEXT_HTML))
                .andExpect(status().isFound())
                .andExpect(header().string(HttpHeaders.LOCATION, startsWith(REDIRECT_URI + "?")))
                .andReturn();
        var callback = UriComponentsBuilder
                .fromUriString(authorizationComplete.getResponse().getRedirectedUrl())
                .build()
                .getQueryParams();
        assertThat(callback.getFirst("error")).isNull();
        assertThat(callback.getFirst("state")).isEqualTo(state);
        assertThat(callback.getFirst("code")).isNotBlank();

        MvcResult tokenResult = mockMvc.perform(post("/oauth2/token")
                        .header(HttpHeaders.ORIGIN, FRONTEND_ORIGIN)
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "authorization_code")
                        .param("client_id", CLIENT_ID)
                        .param("redirect_uri", REDIRECT_URI)
                        .param("code", callback.getFirst("code"))
                        .param("code_verifier", CODE_VERIFIER))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, FRONTEND_ORIGIN))
                .andExpect(jsonPath("$.access_token").isNotEmpty())
                .andReturn();
        JsonNode token = objectMapper.readTree(tokenResult.getResponse().getContentAsString());

        String bearer = "Bearer " + token.get("access_token").asText();
        mockMvc.perform(get("/api/me")
                        .header(HttpHeaders.ORIGIN, FRONTEND_ORIGIN)
                        .header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, FRONTEND_ORIGIN))
                .andExpect(jsonPath("$.username").value("admin"))
                .andExpect(jsonPath("$.isPlatformAdmin").value(true))
                .andExpect(jsonPath("$.scopes", containsInAnyOrder("openid", "profile", "iam.read", "iam.write")));

        mockMvc.perform(get("/api/tenants")
                        .param("page", "0")
                        .param("size", "1")
                        .header(HttpHeaders.ORIGIN, FRONTEND_ORIGIN)
                        .header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, FRONTEND_ORIGIN));
    }

    @Test
    void authorizationEndpointAlwaysStartsInteractiveLoginInsteadOfWhitelabel403() throws Exception {
        mockMvc.perform(get("/oauth2/authorize")
                        .queryParam("response_type", "code")
                        .queryParam("client_id", CLIENT_ID)
                        .queryParam("redirect_uri", REDIRECT_URI)
                        .queryParam("scope", "openid profile iam.read iam.write")
                        .queryParam("state", "entry-point-regression-state")
                        .queryParam("code_challenge", codeChallenge(CODE_VERIFIER))
                        .queryParam("code_challenge_method", "S256")
                        // Interactive authorization is a browser endpoint even if an
                        // intermediary supplies an unexpected Accept header.
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("http://localhost/login"));
    }

    @Test
    void staleOrMissingLoginCsrfStateReturnsIntentionalRecoveryPage() throws Exception {
        MvcResult rejected = mockMvc.perform(post("/login")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("username", "development/admin")
                        .param("password", "admin123456")
                        .param("_csrf", "expired-login-form-token"))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/login?reason=request"))
                .andReturn();

        mockMvc.perform(get(rejected.getResponse().getRedirectedUrl()))
                .andExpect(status().isOk())
                .andExpect(result -> assertThat(result.getResponse().getContentAsString())
                        .contains("sign-in page was no longer valid")
                        .doesNotContain("Whitelabel Error Page"));
    }

    private String codeChallenge(String verifier) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(verifier.getBytes(StandardCharsets.US_ASCII));
        return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
    }

    private String renderedCsrfToken(String html) {
        var matcher = CSRF_INPUT.matcher(html);
        assertThat(matcher.find()).as("backend login form has a rendered CSRF token").isTrue();
        return matcher.group(1);
    }
}

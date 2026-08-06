package io.github.doubletree.iam.platform.authorization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import io.github.doubletree.iam.platform.domain.Client;
import io.github.doubletree.iam.platform.domain.Tenant;
import io.github.doubletree.iam.platform.repository.ClientRepository;
import io.github.doubletree.iam.platform.repository.TenantRepository;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(properties = "spring.application.name=identityforge")
@AutoConfigureMockMvc
@Testcontainers
class AuthorizationServerConfigurationTests {

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private RegisteredClientRepository registeredClientRepository;

    @Autowired
    private AuthorizationServerSettings authorizationServerSettings;

    @Autowired
    private JWKSource<SecurityContext> jwkSource;

    @Autowired
    private JwtDecoder jwtDecoder;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private ClientRepository clientRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @DynamicPropertySource
    static void registerDataSourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", postgres::getDriverClassName);
    }

    @BeforeEach
    void seedDevelopmentClient() {
        if (!clientRepository.findAllByClientId("identityforge-dev").isEmpty()) {
            return;
        }

        Tenant tenant = tenantRepository.save(Tenant.create("Authorization Server Test Tenant"));
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
    void contextStartsWithAuthorizationServerConfiguration() {
        RegisteredClient registeredClient = registeredClientRepository.findByClientId("identityforge-dev");

        assertThat(registeredClient).isNotNull();
        assertThat(registeredClient.getClientId()).isEqualTo("identityforge-dev");
        assertThat(authorizationServerSettings).isNotNull();
        assertThat(jwkSource).isNotNull();
        assertThat(jwtDecoder).isNotNull();
    }

    @Test
    void clientCredentialsTokenRequestReturnsAccessToken() throws Exception {
        mockMvc.perform(post("/oauth2/token")
                        .with(httpBasic("identityforge-dev", "secret"))
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "client_credentials")
                        .param("scope", "iam.read"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.access_token").isNotEmpty())
                .andExpect(jsonPath("$.token_type").value("Bearer"))
                .andExpect(jsonPath("$.expires_in").isNumber());
    }

    @Test
    void healthEndpointIsPublic() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void openApiDocsEndpointIsPublic() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("IdentityForge API"))
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.type").value("http"));
    }

    @Test
    void postApiWithoutTokenIsRejected() throws Exception {
        mockMvc.perform(post("/api/tenants")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"No Token Tenant"}
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void postApiWithWriteScopeAndAdminRoleSucceeds() throws Exception {
        mockMvc.perform(post("/api/tenants")
                        .with(jwt()
                                .jwt(jwt -> jwt
                                        .claim("scope", "iam.write")
                                        .claim("effective_roles", Set.of("platform-admin"))))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Write Scope Tenant"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Write Scope Tenant"));
    }

    @Test
    void postApiWithOnlyReadScopeIsRejected() throws Exception {
        mockMvc.perform(post("/api/tenants")
                        .with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_iam.read")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Read Scope Tenant"}
                                """))
                .andExpect(status().isForbidden());
    }
}

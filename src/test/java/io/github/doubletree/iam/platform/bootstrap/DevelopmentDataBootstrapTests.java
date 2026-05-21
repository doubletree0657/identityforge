package io.github.doubletree.iam.platform.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.doubletree.iam.platform.domain.Client;
import io.github.doubletree.iam.platform.domain.ClientStatus;
import io.github.doubletree.iam.platform.domain.ClientType;
import io.github.doubletree.iam.platform.domain.Tenant;
import io.github.doubletree.iam.platform.repository.ClientRepository;
import io.github.doubletree.iam.platform.repository.TenantRepository;
import io.github.doubletree.iam.platform.security.PasswordEncodingConfiguration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@DataJpaTest
@Testcontainers
@ActiveProfiles("dev")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({DevelopmentDataBootstrap.class, PasswordEncodingConfiguration.class})
class DevelopmentDataBootstrapTests {

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private DevelopmentDataBootstrap bootstrap;

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

    @Test
    void devProfileBootstrapCreatesDevelopmentTenantAndClient() {
        bootstrap.initialize();

        Tenant tenant = tenantRepository.findBySlug(DevelopmentDataBootstrap.DEVELOPMENT_TENANT_SLUG)
                .orElseThrow();
        Client client = clientRepository.findByTenantIdAndClientId(
                        tenant.getId(), DevelopmentDataBootstrap.DEVELOPMENT_CLIENT_ID)
                .orElseThrow();

        assertThat(tenant.getName()).isEqualTo(DevelopmentDataBootstrap.DEVELOPMENT_TENANT_NAME);
        assertThat(client.getClientName()).isEqualTo(DevelopmentDataBootstrap.DEVELOPMENT_CLIENT_NAME);
        assertThat(client.getClientType()).isEqualTo(ClientType.CONFIDENTIAL);
        assertThat(client.getStatus()).isEqualTo(ClientStatus.ACTIVE);
        assertThat(client.isRequirePkce()).isFalse();
        assertThat(client.isRequireConsent()).isFalse();
        assertThat(client.getGrantTypes()).containsExactlyInAnyOrder("client_credentials", "authorization_code");
        assertThat(client.getScopes()).containsExactlyInAnyOrder("iam.read", "iam.write");
        assertThat(client.getAuthenticationMethods()).containsExactly("client_secret_basic");
        assertThat(client.getRedirectUris())
                .containsExactlyInAnyOrder(
                        "http://127.0.0.1:8080/oauth2/demo/callback",
                        "http://localhost:5173/oauth2/callback");
    }

    @Test
    void devClientSecretIsEncodedAndBootstrapIsIdempotent() {
        bootstrap.initialize();
        bootstrap.initialize();

        Tenant tenant = tenantRepository.findBySlug(DevelopmentDataBootstrap.DEVELOPMENT_TENANT_SLUG)
                .orElseThrow();
        List<Client> clients = clientRepository.findAllByClientId(DevelopmentDataBootstrap.DEVELOPMENT_CLIENT_ID);

        assertThat(clients).hasSize(1);
        assertThat(clients.getFirst().getTenant().getId()).isEqualTo(tenant.getId());
        assertThat(clients.getFirst().getClientSecretHash()).isNotEqualTo("secret");
        assertThat(passwordEncoder.matches("secret", clients.getFirst().getClientSecretHash())).isTrue();
    }
}

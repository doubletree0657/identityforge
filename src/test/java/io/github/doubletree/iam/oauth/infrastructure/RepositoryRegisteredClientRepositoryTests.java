package io.github.doubletree.iam.oauth.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.doubletree.iam.applications.domain.Client;
import io.github.doubletree.iam.applications.domain.ClientStatus;
import io.github.doubletree.iam.applications.domain.ClientType;
import io.github.doubletree.iam.applications.domain.ResourcePermission;
import io.github.doubletree.iam.applications.domain.ResourceServer;
import io.github.doubletree.iam.applications.domain.ResourceServerStatus;
import io.github.doubletree.iam.directory.domain.Tenant;
import io.github.doubletree.iam.directory.domain.TenantStatus;
import io.github.doubletree.iam.applications.infrastructure.persistence.ClientRepository;
import io.github.doubletree.iam.applications.infrastructure.persistence.ResourcePermissionRepository;
import io.github.doubletree.iam.applications.infrastructure.persistence.ResourceServerRepository;
import io.github.doubletree.iam.directory.infrastructure.persistence.TenantRepository;
import java.time.Duration;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.settings.OAuth2TokenFormat;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@DataJpaTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({RepositoryRegisteredClientRepository.class, RegisteredClientMapper.class})
class RepositoryRegisteredClientRepositoryTests {

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private ClientRepository clientRepository;

    @Autowired
    private ResourceServerRepository resourceServerRepository;

    @Autowired
    private ResourcePermissionRepository resourcePermissionRepository;

    @Autowired
    private RepositoryRegisteredClientRepository registeredClientRepository;

    @DynamicPropertySource
    static void registerDataSourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", postgres::getDriverClassName);
    }

    @Test
    void persistedConfidentialClientCanBeLoadedByClientId() {
        Client client = saveConfidentialClient();

        RegisteredClient registeredClient = registeredClientRepository.findByClientId("registered-confidential");

        assertThat(registeredClient).isNotNull();
        assertThat(registeredClient.getId()).isEqualTo(client.getId().toString());
        assertThat(registeredClient.getClientId()).isEqualTo("registered-confidential");
        assertThat(registeredClient.getClientSecret()).isEqualTo("{bcrypt}stored-client-secret-hash");
        assertThat(registeredClient.getClientSecret()).isNotEqualTo("raw-client-secret");
        assertThat(registeredClient.getClientAuthenticationMethods())
                .containsExactlyInAnyOrder(
                        ClientAuthenticationMethod.CLIENT_SECRET_BASIC,
                        ClientAuthenticationMethod.CLIENT_SECRET_POST);
        assertThat(registeredClient.getAuthorizationGrantTypes())
                .containsExactlyInAnyOrder(
                        AuthorizationGrantType.AUTHORIZATION_CODE,
                        AuthorizationGrantType.CLIENT_CREDENTIALS);
        assertThat(registeredClient.getRedirectUris())
                .containsExactlyInAnyOrder(
                        "https://client.example.test/callback",
                        "https://client.example.test/secondary-callback");
        assertThat(registeredClient.getScopes()).containsExactlyInAnyOrder("iam.read", "iam.write");
        assertThat(registeredClient.getClientSettings().isRequireProofKey()).isFalse();
        assertThat(registeredClient.getClientSettings().isRequireAuthorizationConsent()).isTrue();
    }

    @Test
    void mappedRegisteredClientUsesThirtyMinuteSelfContainedAccessTokens() {
        saveConfidentialClient();

        RegisteredClient registeredClient = registeredClientRepository.findByClientId("registered-confidential");

        assertThat(registeredClient.getTokenSettings().getAccessTokenFormat())
                .isEqualTo(OAuth2TokenFormat.SELF_CONTAINED);
        assertThat(registeredClient.getTokenSettings().getAccessTokenTimeToLive())
                .isEqualTo(Duration.ofMinutes(30));
    }

    @Test
    void persistedClientCanBeLoadedById() {
        Client client = saveConfidentialClient();

        RegisteredClient registeredClient = registeredClientRepository.findById(client.getId().toString());

        assertThat(registeredClient).isNotNull();
        assertThat(registeredClient.getClientId()).isEqualTo("registered-confidential");
    }

    @Test
    void publicClientMapsWithoutClientSecret() {
        Tenant tenant = tenantRepository.save(Tenant.create("Public Registered Client Tenant"));
        Client client = Client.create(tenant, "registered-public", "Registered Public");
        client.setClientType(ClientType.PUBLIC);
        client.setClientSecretHash(null);
        client.setRequirePkce(true);
        client.setRequireConsent(false);
        client.setRedirectUris(Set.of("https://public.example.test/callback"));
        client.setGrantTypes(Set.of("authorization_code"));
        client.setScopes(Set.of("openid", "profile"));
        client.setAuthenticationMethods(Set.of("none"));
        clientRepository.saveAndFlush(client);

        RegisteredClient registeredClient = registeredClientRepository.findByClientId("registered-public");

        assertThat(registeredClient).isNotNull();
        assertThat(registeredClient.getClientSecret()).isNull();
        assertThat(registeredClient.getClientAuthenticationMethods())
                .containsExactly(ClientAuthenticationMethod.NONE);
        assertThat(registeredClient.getAuthorizationGrantTypes())
                .containsExactly(AuthorizationGrantType.AUTHORIZATION_CODE);
        assertThat(registeredClient.getRedirectUris()).containsExactly("https://public.example.test/callback");
        assertThat(registeredClient.getScopes()).containsExactlyInAnyOrder("openid", "profile");
        assertThat(registeredClient.getClientSettings().isRequireProofKey()).isTrue();
        assertThat(registeredClient.getClientSettings().isRequireAuthorizationConsent()).isFalse();
    }

    @Test
    void registeredClientIncludesAllowedApplicationPermissionScopes() {
        Tenant tenant = tenantRepository.save(Tenant.create("Application Scope Tenant"));
        ResourceServer resourceServer = resourceServerRepository.save(
                ResourceServer.create(tenant, "payroll-api", "Payroll API"));
        ResourcePermission permission = resourcePermissionRepository.save(
                ResourcePermission.create(resourceServer, "payroll.employee.read", "Read employees", null));
        Client client = Client.create(tenant, "registered-application-client", "Registered Application Client");
        client.setClientSecretHash("{bcrypt}stored-client-secret-hash");
        client.setRequirePkce(false);
        client.setRedirectUris(Set.of("https://client.example.test/callback"));
        client.setGrantTypes(Set.of("authorization_code"));
        client.setScopes(Set.of("openid"));
        client.setAuthenticationMethods(Set.of("client_secret_basic"));
        client.setResourceServer(resourceServer);
        client.addAllowedResourcePermission(permission);
        clientRepository.saveAndFlush(client);

        RegisteredClient registeredClient = registeredClientRepository.findByClientId("registered-application-client");

        assertThat(registeredClient.getScopes())
                .contains("openid", "payroll.employee.read")
                .doesNotContain("payroll.salary.write");
    }

    @Test
    void registeredClientExcludesAllowedApplicationPermissionFromDifferentResourceServerOrTenant() {
        Tenant tenant = tenantRepository.save(Tenant.create("Filtered Application Scope Tenant"));
        Tenant otherTenant = tenantRepository.save(Tenant.create("Other Filtered Application Scope Tenant"));
        ResourceServer linkedResourceServer = resourceServerRepository.save(
                ResourceServer.create(tenant, "filtered-payroll-api", "Filtered Payroll API"));
        ResourceServer otherResourceServer = resourceServerRepository.save(
                ResourceServer.create(tenant, "filtered-crm-api", "Filtered CRM API"));
        ResourceServer otherTenantResourceServer = resourceServerRepository.save(
                ResourceServer.create(otherTenant, "filtered-external-api", "Filtered External API"));
        ResourcePermission linkedPermission = resourcePermissionRepository.save(
                ResourcePermission.create(linkedResourceServer, "payroll.employee.read", "Read employees", null));
        ResourcePermission otherResourceServerPermission = resourcePermissionRepository.save(
                ResourcePermission.create(otherResourceServer, "crm.customer.read", "Read customers", null));
        ResourcePermission otherTenantPermission = resourcePermissionRepository.save(
                ResourcePermission.create(otherTenantResourceServer, "external.invoice.read", "Read invoices", null));
        Client client = Client.create(tenant, "filtered-application-client", "Filtered Application Client");
        client.setClientSecretHash("{bcrypt}stored-client-secret-hash");
        client.setRequirePkce(false);
        client.setRedirectUris(Set.of("https://client.example.test/callback"));
        client.setGrantTypes(Set.of("authorization_code"));
        client.setScopes(Set.of("openid"));
        client.setAuthenticationMethods(Set.of("client_secret_basic"));
        client.setResourceServer(linkedResourceServer);
        client.addAllowedResourcePermission(linkedPermission);
        client.addAllowedResourcePermission(otherResourceServerPermission);
        client.addAllowedResourcePermission(otherTenantPermission);
        clientRepository.saveAndFlush(client);

        RegisteredClient registeredClient = registeredClientRepository.findByClientId("filtered-application-client");

        assertThat(registeredClient.getScopes())
                .contains("openid", "payroll.employee.read")
                .doesNotContain("crm.customer.read", "external.invoice.read");
    }

    @Test
    void missingClientIdReturnsNull() {
        assertThat(registeredClientRepository.findByClientId("missing-client")).isNull();
    }

    @Test
    void disabledClientIsNotLoaded() {
        Client client = saveConfidentialClient();
        client.setStatus(ClientStatus.DISABLED);
        clientRepository.saveAndFlush(client);

        assertThat(registeredClientRepository.findByClientId("registered-confidential")).isNull();
        assertThat(registeredClientRepository.findById(client.getId().toString())).isNull();
    }

    @Test
    void clientLinkedToInactiveResourceServerIsNotLoaded() {
        Tenant tenant = tenantRepository.save(Tenant.create("Inactive Resource Tenant"));
        ResourceServer resourceServer = ResourceServer.create(tenant, "inactive-api", "Inactive API");
        resourceServer.setStatus(ResourceServerStatus.DISABLED);
        resourceServerRepository.save(resourceServer);
        Client client = Client.create(tenant, "inactive-resource-client", "Inactive Resource Client");
        client.setClientSecretHash("{bcrypt}stored-client-secret-hash");
        client.setRequirePkce(false);
        client.setResourceServer(resourceServer);
        clientRepository.saveAndFlush(client);

        assertThat(registeredClientRepository.findByClientId("inactive-resource-client")).isNull();
    }

    @Test
    void clientOwnedByInactiveTenantIsNotLoaded() {
        Client client = saveConfidentialClient();
        client.getTenant().setStatus(TenantStatus.SUSPENDED);
        tenantRepository.saveAndFlush(client.getTenant());

        assertThat(registeredClientRepository.findByClientId("registered-confidential")).isNull();
    }

    @Test
    void unsupportedSaveFailsClearly() {
        Client client = saveConfidentialClient();
        RegisteredClient registeredClient = registeredClientRepository.findByClientId("registered-confidential");

        assertThatThrownBy(() -> registeredClientRepository.save(registeredClient))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessage("Registered clients must be managed through ClientApplicationService");
        assertThat(clientRepository.findById(client.getId())).isPresent();
    }

    private Client saveConfidentialClient() {
        Tenant tenant = tenantRepository.save(Tenant.create("Registered Client Tenant"));
        Client client = Client.create(tenant, "registered-confidential", "Registered Confidential");
        client.setClientSecretHash("{bcrypt}stored-client-secret-hash");
        client.setRequirePkce(false);
        client.setRequireConsent(true);
        client.setRedirectUris(Set.of(
                "https://client.example.test/callback",
                "https://client.example.test/secondary-callback"));
        client.setGrantTypes(Set.of("authorization_code", "client_credentials"));
        client.setScopes(Set.of("iam.read", "iam.write"));
        client.setAuthenticationMethods(Set.of("client_secret_basic", "client_secret_post"));
        return clientRepository.saveAndFlush(client);
    }
}

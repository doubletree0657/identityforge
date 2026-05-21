package io.github.doubletree.iam.platform.bootstrap;

import io.github.doubletree.iam.platform.domain.Client;
import io.github.doubletree.iam.platform.domain.ClientStatus;
import io.github.doubletree.iam.platform.domain.ClientType;
import io.github.doubletree.iam.platform.domain.Tenant;
import io.github.doubletree.iam.platform.repository.ClientRepository;
import io.github.doubletree.iam.platform.repository.TenantRepository;
import java.util.List;
import java.util.Set;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Profile("dev")
public class DevelopmentDataBootstrap implements ApplicationRunner {

    static final String DEVELOPMENT_TENANT_NAME = "Development Tenant";
    static final String DEVELOPMENT_TENANT_SLUG = "development";
    static final String DEVELOPMENT_CLIENT_ID = "international-iam-dev";
    static final String DEVELOPMENT_CLIENT_NAME = "International IAM Dev Client";
    private static final String DEVELOPMENT_CLIENT_SECRET = "secret";

    private static final Set<String> DEVELOPMENT_REDIRECT_URIS = Set.of(
            "http://127.0.0.1:8080/oauth2/demo/callback",
            "http://localhost:5173/oauth2/callback");
    private static final Set<String> DEVELOPMENT_GRANT_TYPES = Set.of("client_credentials", "authorization_code");
    private static final Set<String> DEVELOPMENT_SCOPES = Set.of("iam.read", "iam.write");
    private static final Set<String> DEVELOPMENT_AUTHENTICATION_METHODS = Set.of("client_secret_basic");

    private final TenantRepository tenantRepository;
    private final ClientRepository clientRepository;
    private final PasswordEncoder passwordEncoder;

    public DevelopmentDataBootstrap(
            TenantRepository tenantRepository,
            ClientRepository clientRepository,
            PasswordEncoder passwordEncoder) {
        this.tenantRepository = tenantRepository;
        this.clientRepository = clientRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        initialize();
    }

    @Transactional
    public void initialize() {
        Tenant tenant = tenantRepository.findBySlug(DEVELOPMENT_TENANT_SLUG)
                .orElseGet(this::createDevelopmentTenant);
        List<Client> existingClients = clientRepository.findAllByClientId(DEVELOPMENT_CLIENT_ID);
        if (existingClients.isEmpty()) {
            createDevelopmentClient(tenant);
            return;
        }
        existingClients.stream()
                .filter(client -> client.getTenant().getId().equals(tenant.getId()))
                .findFirst()
                .or(() -> existingClients.stream().findFirst())
                .ifPresent(this::refreshDevelopmentClient);
    }

    private Tenant createDevelopmentTenant() {
        Tenant tenant = Tenant.create(DEVELOPMENT_TENANT_NAME);
        tenant.setSlug(DEVELOPMENT_TENANT_SLUG);
        return tenantRepository.save(tenant);
    }

    private void createDevelopmentClient(Tenant tenant) {
        Client client = Client.create(tenant, DEVELOPMENT_CLIENT_ID, DEVELOPMENT_CLIENT_NAME);
        refreshDevelopmentClient(client);
    }

    private void refreshDevelopmentClient(Client client) {
        client.setClientName(DEVELOPMENT_CLIENT_NAME);
        client.setClientType(ClientType.CONFIDENTIAL);
        client.setStatus(ClientStatus.ACTIVE);
        client.setRequirePkce(false);
        client.setRequireConsent(false);
        client.setRedirectUris(DEVELOPMENT_REDIRECT_URIS);
        client.setGrantTypes(DEVELOPMENT_GRANT_TYPES);
        client.setScopes(DEVELOPMENT_SCOPES);
        client.setAuthenticationMethods(DEVELOPMENT_AUTHENTICATION_METHODS);
        if (client.getClientSecretHash() == null
                || !passwordEncoder.matches(DEVELOPMENT_CLIENT_SECRET, client.getClientSecretHash())) {
            client.setClientSecretHash(passwordEncoder.encode(DEVELOPMENT_CLIENT_SECRET));
        }
        client.validateRegistration();
        clientRepository.save(client);
    }
}

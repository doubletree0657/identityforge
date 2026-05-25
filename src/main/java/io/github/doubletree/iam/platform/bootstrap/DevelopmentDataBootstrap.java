package io.github.doubletree.iam.platform.bootstrap;

import io.github.doubletree.iam.platform.domain.Client;
import io.github.doubletree.iam.platform.domain.ClientStatus;
import io.github.doubletree.iam.platform.domain.ClientType;
import io.github.doubletree.iam.platform.domain.Permission;
import io.github.doubletree.iam.platform.domain.Role;
import io.github.doubletree.iam.platform.domain.Tenant;
import io.github.doubletree.iam.platform.domain.User;
import io.github.doubletree.iam.platform.application.service.UserApplicationService;
import io.github.doubletree.iam.platform.repository.ClientRepository;
import io.github.doubletree.iam.platform.repository.PermissionRepository;
import io.github.doubletree.iam.platform.repository.RoleRepository;
import io.github.doubletree.iam.platform.repository.TenantRepository;
import io.github.doubletree.iam.platform.repository.UserRepository;
import io.github.doubletree.iam.platform.security.BuiltInPermission;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.beans.factory.annotation.Value;
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
    static final String ADMIN_CONSOLE_CLIENT_ID = "iam-admin-console";
    static final String ADMIN_CONSOLE_CLIENT_NAME = "IAM Admin Console";
    static final String ADMIN_ROLE_NAME = "platform-admin";
    static final String TENANT_ADMIN_ROLE_NAME = "tenant-admin";
    static final String AUDITOR_ROLE_NAME = "auditor";
    static final String ADMIN_DISPLAY_NAME = "Development Super Admin";
    private static final String DEVELOPMENT_CLIENT_SECRET = "secret";

    private static final Set<String> DEVELOPMENT_REDIRECT_URIS = Set.of(
            "http://127.0.0.1:8080/oauth2/demo/callback",
            "http://localhost:5173/oauth2/callback");
    private static final Set<String> DEVELOPMENT_GRANT_TYPES = Set.of("client_credentials", "authorization_code");
    private static final Set<String> DEVELOPMENT_SCOPES = Set.of("iam.read", "iam.write");
    private static final Set<String> DEVELOPMENT_AUTHENTICATION_METHODS = Set.of("client_secret_basic");
    private static final Set<String> ADMIN_CONSOLE_REDIRECT_URIS = Set.of("http://localhost:5173/oauth2/callback");
    private static final Set<String> ADMIN_CONSOLE_GRANT_TYPES = Set.of("authorization_code");
    private static final Set<String> ADMIN_CONSOLE_SCOPES = Set.of("iam.read", "iam.write", "openid", "profile");
    private static final Set<String> ADMIN_CONSOLE_AUTHENTICATION_METHODS = Set.of("none");
    private final TenantRepository tenantRepository;
    private final ClientRepository clientRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final UserApplicationService userApplicationService;
    private final PasswordEncoder passwordEncoder;
    private final boolean adminBootstrapEnabled;
    private final String adminUsername;
    private final String adminPassword;
    private final boolean resetAdminPassword;

    public DevelopmentDataBootstrap(
            TenantRepository tenantRepository,
            ClientRepository clientRepository,
            UserRepository userRepository,
            RoleRepository roleRepository,
            PermissionRepository permissionRepository,
            UserApplicationService userApplicationService,
            PasswordEncoder passwordEncoder,
            @Value("${app.bootstrap.admin.enabled:false}") boolean adminBootstrapEnabled,
            @Value("${app.bootstrap.admin.username:admin}") String adminUsername,
            @Value("${app.bootstrap.admin.password:admin123456}") String adminPassword,
            @Value("${app.bootstrap.admin.reset-password:false}") boolean resetAdminPassword) {
        this.tenantRepository = tenantRepository;
        this.clientRepository = clientRepository;
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.permissionRepository = permissionRepository;
        this.userApplicationService = userApplicationService;
        this.passwordEncoder = passwordEncoder;
        this.adminBootstrapEnabled = adminBootstrapEnabled;
        this.adminUsername = adminUsername;
        this.adminPassword = adminPassword;
        this.resetAdminPassword = resetAdminPassword;
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
        } else {
            existingClients.stream()
                    .filter(client -> client.getTenant().getId().equals(tenant.getId()))
                    .findFirst()
                    .or(() -> existingClients.stream().findFirst())
                    .ifPresent(this::refreshDevelopmentClient);
        }
        initializeAdminConsoleClient(tenant);
        initializeBuiltInPermissionsAndRoles(tenant);
        if (adminBootstrapEnabled) {
            initializeAdminUser(tenant);
        }
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

    private void initializeAdminConsoleClient(Tenant tenant) {
        List<Client> existingClients = clientRepository.findAllByClientId(ADMIN_CONSOLE_CLIENT_ID);
        if (existingClients.isEmpty()) {
            Client client = Client.create(tenant, ADMIN_CONSOLE_CLIENT_ID, ADMIN_CONSOLE_CLIENT_NAME);
            refreshAdminConsoleClient(client);
            return;
        }
        existingClients.stream()
                .filter(client -> client.getTenant().getId().equals(tenant.getId()))
                .findFirst()
                .or(() -> existingClients.stream().findFirst())
                .ifPresent(this::refreshAdminConsoleClient);
    }

    private void refreshAdminConsoleClient(Client client) {
        client.setClientName(ADMIN_CONSOLE_CLIENT_NAME);
        client.setClientSecretHash(null);
        client.setClientType(ClientType.PUBLIC);
        client.setStatus(ClientStatus.ACTIVE);
        client.setRequirePkce(true);
        client.setRequireConsent(false);
        client.setRedirectUris(ADMIN_CONSOLE_REDIRECT_URIS);
        client.setGrantTypes(ADMIN_CONSOLE_GRANT_TYPES);
        client.setScopes(ADMIN_CONSOLE_SCOPES);
        client.setAuthenticationMethods(ADMIN_CONSOLE_AUTHENTICATION_METHODS);
        client.validateRegistration();
        clientRepository.save(client);
    }

    private void initializeAdminUser(Tenant tenant) {
        Role adminRole = roleRepository.findByTenantIdAndName(tenant.getId(), ADMIN_ROLE_NAME)
                .orElseThrow(() -> new IllegalStateException("Platform admin role template was not initialized"));

        User adminUser = userRepository.findByTenantIdAndUsername(tenant.getId(), adminUsername)
                .orElseGet(() -> userApplicationService.createUser(tenant.getId(), adminUsername, ADMIN_DISPLAY_NAME));
        adminUser.getRoles().add(adminRole);
        userRepository.save(adminUser);
        if (adminUser.getPasswordCredential() == null || resetAdminPassword) {
            userApplicationService.setInitialPassword(adminUser.getId(), adminPassword);
        }
    }

    private void initializeBuiltInPermissionsAndRoles(Tenant tenant) {
        Map<String, Permission> permissions = new java.util.LinkedHashMap<>();
        for (BuiltInPermission builtInPermission : BuiltInPermission.values()) {
            Permission permission = permissionRepository.findByTenantIdAndName(
                            tenant.getId(), builtInPermission.permissionName())
                    .orElseGet(() -> Permission.system(
                            tenant,
                            builtInPermission.permissionName(),
                            builtInPermission.displayName(),
                            builtInPermission.description(),
                            builtInPermission.category()));
            permission.setDisplayName(builtInPermission.displayName());
            permission.setDescription(builtInPermission.description());
            permission.setCategory(builtInPermission.category());
            permission.setSystemManaged(true);
            permissions.put(builtInPermission.permissionName(), permissionRepository.save(permission));
        }
        initializeRoleTemplate(tenant, ADMIN_ROLE_NAME, BuiltInPermission.platformAdminNames(), permissions);
        initializeRoleTemplate(tenant, TENANT_ADMIN_ROLE_NAME, BuiltInPermission.tenantAdminNames(), permissions);
        initializeRoleTemplate(tenant, AUDITOR_ROLE_NAME, BuiltInPermission.auditorNames(), permissions);
    }

    private void initializeRoleTemplate(
            Tenant tenant,
            String roleName,
            Set<String> permissionNames,
            Map<String, Permission> permissions) {
        Role role = roleRepository.findByTenantIdAndName(tenant.getId(), roleName)
                .orElseGet(() -> roleRepository.save(Role.create(tenant, roleName)));
        permissionNames.stream()
                .map(permissions::get)
                .forEach(role.getPermissions()::add);
        roleRepository.save(role);
    }
}

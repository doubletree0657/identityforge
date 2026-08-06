package io.github.doubletree.iam.domain;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.doubletree.iam.repository.ClientRepository;
import io.github.doubletree.iam.repository.GroupMembershipRepository;
import io.github.doubletree.iam.repository.GroupRepository;
import io.github.doubletree.iam.repository.PasswordCredentialRepository;
import io.github.doubletree.iam.repository.TotpCredentialRepository;
import io.github.doubletree.iam.repository.UserAttributeRepository;
import io.github.doubletree.iam.repository.UserProfileRepository;
import io.github.doubletree.iam.repository.PermissionRepository;
import io.github.doubletree.iam.repository.RoleRepository;
import io.github.doubletree.iam.repository.TenantRepository;
import io.github.doubletree.iam.repository.UserRepository;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@DataJpaTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class DomainPersistenceTests {

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordCredentialRepository passwordCredentialRepository;

    @Autowired
    private TotpCredentialRepository totpCredentialRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PermissionRepository permissionRepository;

    @Autowired
    private ClientRepository clientRepository;

    @Autowired
    private GroupRepository groupRepository;

    @Autowired
    private GroupMembershipRepository groupMembershipRepository;

    @Autowired
    private UserProfileRepository userProfileRepository;

    @Autowired
    private UserAttributeRepository userAttributeRepository;

    @Autowired
    private EntityManager entityManager;

    @DynamicPropertySource
    static void registerDataSourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", postgres::getDriverClassName);
    }

    @Test
    void tenantCanBeSavedAndLoaded() {
        Tenant tenant = tenantRepository.save(tenant("Acme"));

        flushAndClear();

        Tenant loadedTenant = tenantRepository.findById(tenant.getId()).orElseThrow();

        assertThat(loadedTenant.getName()).isEqualTo("Acme");
        assertThat(loadedTenant.getSlug()).isEqualTo("acme");
        assertThat(loadedTenant.getCreatedAt()).isNotNull();
        assertThat(loadedTenant.getUpdatedAt()).isNotNull();
    }

    @Test
    void userBelongsToTenant() {
        Tenant tenant = tenantRepository.save(tenant("User Tenant"));
        User user = userRepository.save(user(tenant, "alice", "Alice Example"));

        flushAndClear();

        User loadedUser = userRepository.findById(user.getId()).orElseThrow();

        assertThat(loadedUser.getTenant().getId()).isEqualTo(tenant.getId());
        assertThat(loadedUser.getUsername()).isEqualTo("alice");
    }

    @Test
    void newUserDefaultsToPendingCredentialState() {
        Tenant tenant = tenantRepository.save(tenant("User Defaults Tenant"));
        User user = userRepository.save(user(tenant, "pending-user", "Pending User"));

        flushAndClear();

        User loadedUser = userRepository.findById(user.getId()).orElseThrow();

        assertThat(loadedUser.getPasswordCredential()).isNull();
        assertThat(loadedUser.getAccountStatus()).isEqualTo(AccountStatus.PENDING);
    }

    @Test
    void userPasswordCredentialsAndAccountStatusCanBeSavedAndLoaded() {
        Tenant tenant = tenantRepository.save(tenant("Credential Tenant"));
        Instant passwordUpdatedAt = Instant.parse("2026-01-01T00:00:00Z");
        User user = user(tenant, "credential-user", "Credential User");
        PasswordCredential credential = user.ensurePasswordCredential();
        credential.setPasswordHash("$2a$10$storedPasswordHashOnly");
        credential.setPasswordUpdatedAt(passwordUpdatedAt);
        credential.setPasswordResetRequired(false);
        credential.setCredentialsVersion(2);
        user.setAccountStatus(AccountStatus.LOCKED);
        User savedUser = userRepository.save(user);

        flushAndClear();

        User loadedUser = userRepository.findById(savedUser.getId()).orElseThrow();
        PasswordCredential loadedCredential = passwordCredentialRepository.findByUserId(savedUser.getId()).orElseThrow();

        assertThat(loadedCredential.getPasswordHash()).isEqualTo("$2a$10$storedPasswordHashOnly");
        assertThat(loadedCredential.getPasswordUpdatedAt()).isEqualTo(passwordUpdatedAt);
        assertThat(loadedCredential.isPasswordResetRequired()).isFalse();
        assertThat(loadedCredential.getCredentialsVersion()).isEqualTo(2);
        assertThat(loadedUser.getAccountStatus()).isEqualTo(AccountStatus.LOCKED);
    }

    @Test
    void totpCredentialCanBeSavedAndLoadedSeparatelyFromUser() {
        Tenant tenant = tenantRepository.save(tenant("TOTP Credential Tenant"));
        User user = userRepository.save(user(tenant, "totp-user", "TOTP User"));
        TotpCredential credential = totpCredentialRepository.save(
                TotpCredential.create(user, "encrypted-secret-only"));

        flushAndClear();

        User loadedUser = userRepository.findById(user.getId()).orElseThrow();
        TotpCredential loadedCredential = totpCredentialRepository.findByUserId(user.getId()).orElseThrow();

        assertThat(loadedUser.getClass().getDeclaredFields())
                .extracting("name")
                .doesNotContain("mfaSecret", "mfaEnabled");
        assertThat(loadedCredential.getId()).isEqualTo(credential.getId());
        assertThat(loadedCredential.getSecretCiphertext()).isEqualTo("encrypted-secret-only");
        assertThat(loadedCredential.isEnabled()).isTrue();
    }

    @Test
    void userProfileAndAttributesCanBeSavedAndLoadedSeparatelyFromCoreIdentity() {
        Tenant tenant = tenantRepository.save(tenant("Profile Tenant"));
        User user = userRepository.save(user(tenant, "profile-user", "Profile User"));

        UserProfile profile = UserProfile.create(user);
        profile.setGivenName("Profile");
        profile.setFamilyName("User");
        profile.setLocale("en-US");
        profile.setDepartment("Engineering");
        userProfileRepository.save(profile);

        userAttributeRepository.save(UserAttribute.create(
                user, "costCenter", "iam-platform", UserAttributeValueType.STRING));

        flushAndClear();

        User loadedUser = userRepository.findById(user.getId()).orElseThrow();
        UserProfile loadedProfile = userProfileRepository.findAll().getFirst();
        UserAttribute loadedAttribute = userAttributeRepository.findAll().getFirst();

        assertThat(loadedUser.getEmail()).isNull();
        assertThat(loadedProfile.getUser().getId()).isEqualTo(user.getId());
        assertThat(loadedProfile.getDepartment()).isEqualTo("Engineering");
        assertThat(loadedAttribute.getUser().getId()).isEqualTo(user.getId());
        assertThat(loadedAttribute.getName()).isEqualTo("costCenter");
        assertThat(loadedAttribute.getValueType()).isEqualTo(UserAttributeValueType.STRING);
    }

    @Test
    void roleBelongsToTenant() {
        Tenant tenant = tenantRepository.save(tenant("Role Tenant"));
        Role role = roleRepository.save(role(tenant, "admin"));

        flushAndClear();

        Role loadedRole = roleRepository.findById(role.getId()).orElseThrow();

        assertThat(loadedRole.getTenant().getId()).isEqualTo(tenant.getId());
        assertThat(loadedRole.getName()).isEqualTo("admin");
    }

    @Test
    void userCanHaveRoles() {
        Tenant tenant = tenantRepository.save(tenant("User Role Tenant"));
        Role role = roleRepository.save(role(tenant, "operator"));
        User user = user(tenant, "bob", "Bob Example");
        user.getRoles().add(role);
        User savedUser = userRepository.save(user);

        flushAndClear();

        User loadedUser = userRepository.findById(savedUser.getId()).orElseThrow();

        assertThat(loadedUser.getRoles())
                .extracting(Role::getName)
                .containsExactly("operator");
    }

    @Test
    void groupMembershipConnectsUsersAndGroupsExplicitly() {
        Tenant tenant = tenantRepository.save(tenant("Membership Tenant"));
        User user = userRepository.save(user(tenant, "member", "Member User"));
        Group group = groupRepository.save(group(tenant, "engineering"));
        group.addUser(user);
        groupRepository.save(group);

        flushAndClear();

        Group loadedGroup = groupRepository.findById(group.getId()).orElseThrow();

        assertThat(groupMembershipRepository.findAll()).hasSize(1);
        assertThat(loadedGroup.getUsers())
                .extracting(User::getUsername)
                .containsExactly("member");
    }

    @Test
    void roleCanHavePermissions() {
        Tenant tenant = tenantRepository.save(tenant("Role Permission Tenant"));
        Permission permission = permissionRepository.save(permission("clients:read"));
        Role role = role(tenant, "auditor");
        role.getPermissions().add(permission);
        Role savedRole = roleRepository.save(role);

        flushAndClear();

        Role loadedRole = roleRepository.findById(savedRole.getId()).orElseThrow();

        assertThat(loadedRole.getPermissions())
                .extracting(Permission::getName)
                .containsExactly("clients:read");
    }

    @Test
    void clientBelongsToTenant() {
        Tenant tenant = tenantRepository.save(tenant("Client Tenant"));
        Client client = clientRepository.save(client(tenant, "portal", "Portal"));

        flushAndClear();

        Client loadedClient = clientRepository.findById(client.getId()).orElseThrow();

        assertThat(loadedClient.getTenant().getId()).isEqualTo(tenant.getId());
        assertThat(loadedClient.getClientId()).isEqualTo("portal");
        assertThat(loadedClient.getClientType()).isEqualTo(ClientType.CONFIDENTIAL);
        assertThat(loadedClient.getStatus()).isEqualTo(ClientStatus.ACTIVE);
        assertThat(loadedClient.isRequirePkce()).isTrue();
        assertThat(loadedClient.isRequireConsent()).isTrue();
        assertThat(loadedClient.getGrantTypes()).containsExactly("authorization_code");
        assertThat(loadedClient.getScopes()).containsExactly("iam.read");
        assertThat(loadedClient.getAuthenticationMethods()).containsExactly("client_secret_basic");
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }

    private Tenant tenant(String name) {
        Tenant tenant = new Tenant();
        tenant.setName(name);
        return tenant;
    }

    private User user(Tenant tenant, String username, String displayName) {
        User user = new User();
        user.setTenant(tenant);
        user.setUsername(username);
        user.setDisplayName(displayName);
        return user;
    }

    private Role role(Tenant tenant, String name) {
        Role role = new Role();
        role.setTenant(tenant);
        role.setName(name);
        return role;
    }

    private Permission permission(String name) {
        Permission permission = new Permission();
        permission.setName(name);
        return permission;
    }

    private Client client(Tenant tenant, String clientId, String name) {
        return Client.create(tenant, clientId, name);
    }

    private Group group(Tenant tenant, String name) {
        Group group = new Group();
        group.setTenant(tenant);
        group.setName(name);
        group.setDisplayName(name);
        return group;
    }
}

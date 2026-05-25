package io.github.doubletree.iam.platform.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.doubletree.iam.platform.application.exception.ClientValidationException;
import io.github.doubletree.iam.platform.application.exception.PasswordValidationException;
import io.github.doubletree.iam.platform.application.exception.TenantBoundaryViolationException;
import io.github.doubletree.iam.platform.application.exception.ValidationException;
import io.github.doubletree.iam.platform.application.result.ClientSecretResult;
import io.github.doubletree.iam.platform.application.result.MfaEnrollmentResult;
import io.github.doubletree.iam.platform.domain.AccountStatus;
import io.github.doubletree.iam.platform.domain.AuditLog;
import io.github.doubletree.iam.platform.domain.AuditActorType;
import io.github.doubletree.iam.platform.domain.Client;
import io.github.doubletree.iam.platform.domain.ClientStatus;
import io.github.doubletree.iam.platform.domain.ClientType;
import io.github.doubletree.iam.platform.domain.Group;
import io.github.doubletree.iam.platform.domain.PasswordCredential;
import io.github.doubletree.iam.platform.domain.Permission;
import io.github.doubletree.iam.platform.domain.Role;
import io.github.doubletree.iam.platform.domain.Tenant;
import io.github.doubletree.iam.platform.domain.TotpCredential;
import io.github.doubletree.iam.platform.domain.User;
import io.github.doubletree.iam.platform.domain.UserAttributeValueType;
import io.github.doubletree.iam.platform.repository.AuditLogRepository;
import io.github.doubletree.iam.platform.repository.ClientRepository;
import io.github.doubletree.iam.platform.repository.GroupRepository;
import io.github.doubletree.iam.platform.repository.PasswordCredentialRepository;
import io.github.doubletree.iam.platform.repository.PermissionRepository;
import io.github.doubletree.iam.platform.repository.RoleRepository;
import io.github.doubletree.iam.platform.repository.TenantRepository;
import io.github.doubletree.iam.platform.repository.TotpCredentialRepository;
import io.github.doubletree.iam.platform.repository.UserRepository;
import io.github.doubletree.iam.platform.security.PasswordEncodingConfiguration;
import io.github.doubletree.iam.platform.security.AdminAuthorizationService;
import io.github.doubletree.iam.platform.security.crypto.SecretEncryptionService;
import io.github.doubletree.iam.platform.web.dto.ClientResponse;
import io.github.doubletree.iam.platform.web.dto.UserResponse;
import java.lang.reflect.RecordComponent;
import java.time.Instant;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@DataJpaTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({
        TenantApplicationService.class,
        UserApplicationService.class,
        RoleApplicationService.class,
        PermissionApplicationService.class,
        ClientApplicationService.class,
        GroupApplicationService.class,
        SystemPermissionCatalogService.class,
        AuditApplicationService.class,
        AdminAuthorizationService.class,
        PasswordEncodingConfiguration.class,
        SecretEncryptionService.class,
        MfaApplicationService.class
})
class ApplicationServiceTests {

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private TenantApplicationService tenantApplicationService;

    @Autowired
    private UserApplicationService userApplicationService;

    @Autowired
    private RoleApplicationService roleApplicationService;

    @Autowired
    private PermissionApplicationService permissionApplicationService;

    @Autowired
    private ClientApplicationService clientApplicationService;

    @Autowired
    private GroupApplicationService groupApplicationService;

    @Autowired
    private MfaApplicationService mfaApplicationService;

    @Autowired
    private SecretEncryptionService secretEncryptionService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PermissionRepository permissionRepository;

    @Autowired
    private ClientRepository clientRepository;

    @Autowired
    private GroupRepository groupRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private PasswordCredentialRepository passwordCredentialRepository;

    @Autowired
    private TotpCredentialRepository totpCredentialRepository;

    @DynamicPropertySource
    static void registerDataSourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", postgres::getDriverClassName);
    }

    @Test
    void createsTenant() {
        Tenant tenant = tenantApplicationService.createTenant("Acme");

        Tenant loadedTenant = tenantRepository.findById(tenant.getId()).orElseThrow();

        assertThat(loadedTenant.getName()).isEqualTo("Acme");
        assertThat(loadedTenant.getSlug()).isEqualTo("acme");
    }

    @Test
    void rejectsDuplicateTenantSlugOnCreate() {
        tenantApplicationService.createTenant("Duplicate Tenant");

        assertThatThrownBy(() -> tenantApplicationService.createTenant("Duplicate Tenant"))
                .isInstanceOf(ValidationException.class)
                .hasMessage("Tenant slug already exists: duplicate-tenant");
    }

    @Test
    void rejectsUpdatingTenantSlugToAnotherTenantSlug() {
        Tenant firstTenant = tenantApplicationService.createTenant("First Tenant");
        Tenant secondTenant = tenantApplicationService.createTenant("Second Tenant");

        assertThatThrownBy(() -> tenantApplicationService.updateTenant(
                        secondTenant.getId(), null, firstTenant.getSlug(), null))
                .isInstanceOf(ValidationException.class)
                .hasMessage("Tenant slug already exists: first-tenant");
    }

    @Test
    void allowsUpdatingTenantWithoutChangingSlug() {
        Tenant tenant = tenantApplicationService.createTenant("Stable Tenant");

        Tenant updatedTenant = tenantApplicationService.updateTenant(
                tenant.getId(), "Stable Tenant Updated", tenant.getSlug(), null);

        assertThat(updatedTenant.getSlug()).isEqualTo("stable-tenant");
        assertThat(updatedTenant.getName()).isEqualTo("Stable Tenant Updated");
    }

    @Test
    void rejectsSecretLikeUserAttributeNamesWithGenericValidationException() {
        Tenant tenant = tenantApplicationService.createTenant("Attribute Validation Tenant");
        User user = userApplicationService.createUser(tenant.getId(), "attribute-user", "Attribute User");

        assertThatThrownBy(() -> userApplicationService.setAttribute(
                        user.getId(), "apiSecret", "sensitive", UserAttributeValueType.STRING))
                .isInstanceOf(ValidationException.class)
                .hasMessage("Secret-like values must not be stored as user attributes");
    }

    @Test
    void createsAuditLogWhenTenantIsCreated() {
        Tenant tenant = tenantApplicationService.createTenant("Audit Tenant");

        assertThat(auditLogRepository.findByAction("TENANT_CREATED"))
                .singleElement()
                .satisfies(auditLog -> {
                    assertThat(auditLog.getTenantId()).isEqualTo(tenant.getId());
                    assertThat(auditLog.getActorType()).isEqualTo(AuditActorType.API_CLIENT);
                    assertThat(auditLog.getResourceType()).isEqualTo("TENANT");
                    assertThat(auditLog.getResourceId()).isEqualTo(tenant.getId());
                    assertThat(auditLog.getCreatedAt()).isNotNull();
                });
    }

    @Test
    void createsUserUnderTenant() {
        Tenant tenant = tenantApplicationService.createTenant("User Tenant");

        User user = userApplicationService.createUser(tenant.getId(), "alice", "Alice Example");

        User loadedUser = userRepository.findById(user.getId()).orElseThrow();
        assertThat(loadedUser.getTenant().getId()).isEqualTo(tenant.getId());
        assertThat(loadedUser.getUsername()).isEqualTo("alice");
        assertThat(loadedUser.getDisplayName()).isEqualTo("Alice Example");
        assertThat(loadedUser.getAccountStatus()).isEqualTo(AccountStatus.PENDING);
        assertThat(loadedUser.getPasswordCredential()).isNull();
    }

    @Test
    void profileLookupReturnsDefaultProfileWhenUserHasNoProfileYet() {
        Tenant tenant = tenantApplicationService.createTenant("Default Profile Tenant");
        User user = userApplicationService.createUser(tenant.getId(), "profile-user", "Profile User");

        var profile = userApplicationService.findProfileByUserId(user.getId());

        assertThat(profile.getId()).isNull();
        assertThat(profile.getUser().getId()).isEqualTo(user.getId());
        assertThat(profile.getGivenName()).isNull();
    }

    @Test
    void encryptsAndDecryptsSecret() {
        String plaintext = "TOTPSECRET123";

        String ciphertext = secretEncryptionService.encrypt(plaintext);

        assertThat(ciphertext).isNotEqualTo(plaintext);
        assertThat(secretEncryptionService.decrypt(ciphertext)).isEqualTo(plaintext);
    }

    @Test
    void enrollingMfaGeneratesSecretAndStoresEncryptedSecret() {
        Tenant tenant = tenantApplicationService.createTenant("MFA Enrollment Tenant");
        User user = userApplicationService.createUser(tenant.getId(), "mfa-user", "MFA User");

        MfaEnrollmentResult enrollment = mfaApplicationService.enrollTotp(user.getId());

        User loadedUser = userRepository.findById(user.getId()).orElseThrow();
        TotpCredential credential = totpCredentialRepository.findByUserId(user.getId()).orElseThrow();
        assertThat(enrollment.userId()).isEqualTo(user.getId());
        assertThat(enrollment.secret()).isNotBlank();
        assertThat(loadedUser.getClass().getDeclaredFields())
                .extracting("name")
                .doesNotContain("mfaSecret", "mfaEnabled");
        assertThat(credential.isEnabled()).isTrue();
        assertThat(credential.getSecretCiphertext()).isNotEqualTo(enrollment.secret());
        assertThat(secretEncryptionService.decrypt(credential.getSecretCiphertext())).isEqualTo(enrollment.secret());
    }

    @Test
    void verifyingValidTotpCodeSucceeds() {
        Tenant tenant = tenantApplicationService.createTenant("MFA Verify Tenant");
        User user = userApplicationService.createUser(tenant.getId(), "verify-user", "Verify User");
        MfaEnrollmentResult enrollment = mfaApplicationService.enrollTotp(user.getId());
        String code = mfaApplicationService.generateTotpCode(enrollment.secret(), Instant.now());

        assertThat(mfaApplicationService.verifyTotp(user.getId(), code)).isTrue();
    }

    @Test
    void verifyingInvalidTotpCodeFails() {
        Tenant tenant = tenantApplicationService.createTenant("MFA Invalid Tenant");
        User user = userApplicationService.createUser(tenant.getId(), "invalid-user", "Invalid User");
        MfaEnrollmentResult enrollment = mfaApplicationService.enrollTotp(user.getId());
        String validCode = mfaApplicationService.generateTotpCode(enrollment.secret(), Instant.now());
        String invalidCode = validCode.equals("000000") ? "000001" : "000000";

        assertThat(mfaApplicationService.verifyTotp(user.getId(), invalidCode)).isFalse();
    }

    @ParameterizedTest
    @CsvSource({
            "59,287082",
            "1111111109,081804",
            "1111111111,050471",
            "1234567890,005924",
            "2000000000,279037",
            "20000000000,353130"
    })
    void generatesSixDigitTotpCodesFromRfc6238Sha1Vectors(long epochSecond, String expectedCode) {
        // RFC 6238 publishes 8-digit SHA-1 values; this local MVP intentionally supports 6-digit TOTP only.
        String rfc6238Sha1Secret = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ";

        String code = mfaApplicationService.generateTotpCode(rfc6238Sha1Secret, Instant.ofEpochSecond(epochSecond));

        assertThat(code).isEqualTo(expectedCode);
    }

    @Test
    void disablingMfaClearsMfaState() {
        Tenant tenant = tenantApplicationService.createTenant("MFA Disable Tenant");
        User user = userApplicationService.createUser(tenant.getId(), "disable-user", "Disable User");
        mfaApplicationService.enrollTotp(user.getId());

        mfaApplicationService.disableTotp(user.getId());

        assertThat(totpCredentialRepository.findByUserId(user.getId())).isEmpty();
    }

    @Test
    void recordsAuditEventsForMfaOperations() {
        Tenant tenant = tenantApplicationService.createTenant("MFA Audit Tenant");
        User user = userApplicationService.createUser(tenant.getId(), "audit-mfa-user", "Audit MFA User");
        MfaEnrollmentResult enrollment = mfaApplicationService.enrollTotp(user.getId());
        String code = mfaApplicationService.generateTotpCode(enrollment.secret(), Instant.now());

        mfaApplicationService.verifyTotp(user.getId(), code);
        mfaApplicationService.disableTotp(user.getId());

        assertThat(auditLogRepository.findByAction("MFA_ENROLLED"))
                .singleElement()
                .satisfies(auditLog -> {
                    assertThat(auditLog.getTenantId()).isEqualTo(tenant.getId());
                    assertThat(auditLog.getResourceType()).isEqualTo("USER");
                    assertThat(auditLog.getResourceId()).isEqualTo(user.getId());
                });
        assertThat(auditLogRepository.findByAction("MFA_VERIFIED"))
                .singleElement()
                .satisfies(auditLog -> assertThat(auditLog.getResourceId()).isEqualTo(user.getId()));
        assertThat(auditLogRepository.findByAction("MFA_DISABLED"))
                .singleElement()
                .satisfies(auditLog -> assertThat(auditLog.getResourceId()).isEqualTo(user.getId()));
    }

    @Test
    void userResponseDoesNotExposeMfaSecret() {
        Tenant tenant = tenantApplicationService.createTenant("MFA Response Tenant");
        User user = userApplicationService.createUser(tenant.getId(), "response-user", "Response User");
        MfaEnrollmentResult enrollment = mfaApplicationService.enrollTotp(user.getId());
        User loadedUser = userRepository.findById(user.getId()).orElseThrow();

        UserResponse response = UserResponse.from(loadedUser);

        assertThat(UserResponse.class.getRecordComponents())
                .extracting(RecordComponent::getName)
                .doesNotContain("mfaSecret");
        assertThat(response.toString()).doesNotContain(enrollment.secret());
    }

    @Test
    void userResponseDoesNotExposePasswordCredentialFields() {
        Tenant tenant = tenantApplicationService.createTenant("Password Response Tenant");
        User user = userApplicationService.createUser(tenant.getId(), "password-response-user", "Password Response");
        PasswordCredential credential = user.ensurePasswordCredential();
        credential.setPasswordHash("$2a$10$sensitiveHash");
        credential.setPasswordUpdatedAt(Instant.parse("2026-01-01T00:00:00Z"));
        credential.setPasswordResetRequired(false);
        credential.setCredentialsVersion(3);
        User savedUser = userRepository.save(user);

        UserResponse response = UserResponse.from(savedUser);

        assertThat(UserResponse.class.getRecordComponents())
                .extracting(RecordComponent::getName)
                .doesNotContain("passwordHash", "passwordUpdatedAt", "passwordResetRequired", "credentialsVersion");
        assertThat(response.toString()).doesNotContain("$2a$10$sensitiveHash");
        assertThat(response.accountStatus()).isEqualTo(AccountStatus.PENDING);
    }

    @Test
    void settingInitialPasswordStoresEncodedHashAndActivatesUser() {
        Tenant tenant = tenantApplicationService.createTenant("Initial Password Tenant");
        User user = userApplicationService.createUser(tenant.getId(), "initial-password-user", "Initial Password");
        Instant beforePasswordSet = Instant.now();

        User updatedUser = userApplicationService.setInitialPassword(user.getId(), "initial-password-123");

        User loadedUser = userRepository.findById(updatedUser.getId()).orElseThrow();
        PasswordCredential credential = loadedUser.getPasswordCredential();
        assertThat(credential.getPasswordHash()).isNotBlank();
        assertThat(credential.getPasswordHash()).isNotEqualTo("initial-password-123");
        assertThat(passwordEncoder.matches("initial-password-123", credential.getPasswordHash())).isTrue();
        assertThat(credential.getPasswordUpdatedAt()).isAfterOrEqualTo(beforePasswordSet);
        assertThat(credential.isPasswordResetRequired()).isFalse();
        assertThat(credential.getCredentialsVersion()).isEqualTo(2);
        assertThat(loadedUser.getAccountStatus()).isEqualTo(AccountStatus.ACTIVE);
    }

    @Test
    void updatingPasswordChangesHashAndIncrementsCredentialsVersion() {
        Tenant tenant = tenantApplicationService.createTenant("Update Password Tenant");
        User user = userApplicationService.createUser(tenant.getId(), "update-password-user", "Update Password");
        User initialPasswordUser = userApplicationService.setInitialPassword(user.getId(), "first-password-123");
        String initialPasswordHash = initialPasswordUser.getPasswordCredential().getPasswordHash();
        Instant initialPasswordUpdatedAt = initialPasswordUser.getPasswordCredential().getPasswordUpdatedAt();

        User updatedUser = userApplicationService.updatePassword(user.getId(), "second-password-123");

        PasswordCredential credential = updatedUser.getPasswordCredential();
        assertThat(credential.getPasswordHash()).isNotEqualTo(initialPasswordHash);
        assertThat(passwordEncoder.matches("second-password-123", credential.getPasswordHash())).isTrue();
        assertThat(credential.getCredentialsVersion()).isEqualTo(3);
        assertThat(credential.getPasswordUpdatedAt()).isAfterOrEqualTo(initialPasswordUpdatedAt);
        assertThat(credential.isPasswordResetRequired()).isFalse();
    }

    @Test
    void passwordResetRequirementCanBeSetAndClearedWithoutChangingCredentialsVersion() {
        Tenant tenant = tenantApplicationService.createTenant("Password Reset Flag Tenant");
        User user = userApplicationService.createUser(tenant.getId(), "reset-flag-user", "Reset Flag");
        User passwordUser = userApplicationService.setInitialPassword(user.getId(), "reset-flag-password");

        User resetRequiredUser = userApplicationService.requirePasswordReset(passwordUser.getId());
        assertThat(resetRequiredUser.getPasswordCredential().isPasswordResetRequired()).isTrue();
        assertThat(resetRequiredUser.getPasswordCredential().getCredentialsVersion()).isEqualTo(2);

        User clearedUser = userApplicationService.clearPasswordResetRequired(passwordUser.getId());
        assertThat(clearedUser.getPasswordCredential().isPasswordResetRequired()).isFalse();
        assertThat(clearedUser.getPasswordCredential().getCredentialsVersion()).isEqualTo(2);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "short"})
    void invalidPasswordInputIsRejected(String invalidPassword) {
        Tenant tenant = tenantApplicationService.createTenant("Invalid Password Tenant");
        User user = userApplicationService.createUser(tenant.getId(), "invalid-password-user", "Invalid Password");

        assertThatThrownBy(() -> userApplicationService.setInitialPassword(user.getId(), invalidPassword))
                .isInstanceOf(PasswordValidationException.class);
    }

    @Test
    void passwordAuditLogsDoNotIncludeRawPasswordOrHash() {
        Tenant tenant = tenantApplicationService.createTenant("Password Audit Tenant");
        User user = userApplicationService.createUser(tenant.getId(), "password-audit-user", "Password Audit");
        String rawPassword = "audit-password-123";

        User updatedUser = userApplicationService.setInitialPassword(user.getId(), rawPassword);

        assertThat(auditLogRepository.findByAction("USER_PASSWORD_SET"))
                .singleElement()
                .satisfies(auditLog -> {
                    assertThat(auditLog.getTenantId()).isEqualTo(tenant.getId());
                    assertThat(auditLog.getResourceType()).isEqualTo("USER");
                    assertThat(auditLog.getResourceId()).isEqualTo(user.getId());
                    assertAuditLogDoesNotContain(auditLog, rawPassword);
                    assertAuditLogDoesNotContain(auditLog, updatedUser.getPasswordCredential().getPasswordHash());
                });
    }

    @Test
    void passwordCredentialRepositoryOwnsPasswordState() {
        Tenant tenant = tenantApplicationService.createTenant("Password Repository Tenant");
        User user = userApplicationService.createUser(tenant.getId(), "credential-owner", "Credential Owner");

        userApplicationService.setInitialPassword(user.getId(), "repository-password-123");

        PasswordCredential credential = passwordCredentialRepository.findByUserId(user.getId()).orElseThrow();
        assertThat(credential.getUser().getId()).isEqualTo(user.getId());
        assertThat(passwordEncoder.matches("repository-password-123", credential.getPasswordHash())).isTrue();
    }

    @Test
    void createsRoleUnderTenant() {
        Tenant tenant = tenantApplicationService.createTenant("Role Tenant");

        Role role = roleApplicationService.createRole(tenant.getId(), "admin");

        Role loadedRole = roleRepository.findById(role.getId()).orElseThrow();
        assertThat(loadedRole.getTenant().getId()).isEqualTo(tenant.getId());
        assertThat(loadedRole.getName()).isEqualTo("admin");
    }

    @Test
    void createsPermission() {
        Tenant tenant = tenantApplicationService.createTenant("Permission Tenant");

        Permission permission = permissionApplicationService.createPermission(tenant.getId(), "clients:read");

        Permission loadedPermission = permissionRepository.findById(permission.getId()).orElseThrow();

        assertThat(loadedPermission.getName()).isEqualTo("clients:read");
    }

    @Test
    void assignsRoleToUser() {
        Tenant tenant = tenantApplicationService.createTenant("Assignment Tenant");
        User user = userApplicationService.createUser(tenant.getId(), "bob", "Bob Example");
        Role role = roleApplicationService.createRole(tenant.getId(), "operator");

        userApplicationService.assignRoleToUser(user.getId(), role.getId());

        User loadedUser = userRepository.findById(user.getId()).orElseThrow();
        assertThat(loadedUser.getRoles())
                .extracting(Role::getName)
                .containsExactly("operator");
    }

    @Test
    void createsAuditLogWhenRoleIsAssignedToUser() {
        Tenant tenant = tenantApplicationService.createTenant("Audit Assignment Tenant");
        User user = userApplicationService.createUser(tenant.getId(), "carol", "Carol Example");
        Role role = roleApplicationService.createRole(tenant.getId(), "reviewer");

        userApplicationService.assignRoleToUser(user.getId(), role.getId());

        assertThat(auditLogRepository.findByAction("ROLE_ASSIGNED_TO_USER"))
                .singleElement()
                .satisfies(auditLog -> {
                    assertThat(auditLog.getTenantId()).isEqualTo(tenant.getId());
                    assertThat(auditLog.getActorType()).isEqualTo(AuditActorType.API_CLIENT);
                    assertThat(auditLog.getResourceType()).isEqualTo("USER");
                    assertThat(auditLog.getResourceId()).isEqualTo(user.getId());
                });
    }

    @Test
    void shouldRejectAssigningRoleFromDifferentTenantToUser() {
        Tenant userTenant = tenantApplicationService.createTenant("User Boundary Tenant");
        Tenant roleTenant = tenantApplicationService.createTenant("Role Boundary Tenant");
        User user = userApplicationService.createUser(userTenant.getId(), "mallory", "Mallory Example");
        Role role = roleApplicationService.createRole(roleTenant.getId(), "external-admin");

        assertThatThrownBy(() -> userApplicationService.assignRoleToUser(user.getId(), role.getId()))
                .isInstanceOf(TenantBoundaryViolationException.class)
                .hasMessage("User and role must belong to the same tenant");

        User loadedUser = userRepository.findById(user.getId()).orElseThrow();
        assertThat(loadedUser.getRoles()).isEmpty();
    }

    @Test
    void assignsPermissionToRole() {
        Tenant tenant = tenantApplicationService.createTenant("Permission Assignment Tenant");
        Role role = roleApplicationService.createRole(tenant.getId(), "auditor");
        Permission permission = permissionApplicationService.createPermission(tenant.getId(), "users:read");

        roleApplicationService.assignPermissionToRole(role.getId(), permission.getId());

        Role loadedRole = roleRepository.findById(role.getId()).orElseThrow();
        assertThat(loadedRole.getPermissions())
                .extracting(Permission::getName)
                .containsExactly("users:read");
    }

    @Test
    void createsClientUnderTenant() {
        Tenant tenant = tenantApplicationService.createTenant("Client Tenant");

        ClientSecretResult result = clientApplicationService.createClientWithSecret(
                tenant.getId(),
                "portal",
                "Portal",
                ClientType.CONFIDENTIAL,
                true,
                true,
                Set.of("https://portal.example.test/callback"),
                Set.of("authorization_code"),
                Set.of("iam.read"),
                Set.of("client_secret_basic"));

        Client loadedClient = clientRepository.findById(result.client().getId()).orElseThrow();
        assertThat(loadedClient.getTenant().getId()).isEqualTo(tenant.getId());
        assertThat(loadedClient.getClientId()).isEqualTo("portal");
        assertThat(loadedClient.getClientName()).isEqualTo("Portal");
        assertThat(loadedClient.getClientType()).isEqualTo(ClientType.CONFIDENTIAL);
        assertThat(result.clientSecret()).isNotBlank();
        assertThat(loadedClient.getClientSecretHash()).isNotBlank();
        assertThat(loadedClient.getClientSecretHash()).isNotEqualTo(result.clientSecret());
        assertThat(passwordEncoder.matches(result.clientSecret(), loadedClient.getClientSecretHash())).isTrue();
        assertThat(ClientResponse.class.getRecordComponents())
                .extracting(RecordComponent::getName)
                .doesNotContain("clientSecret", "clientSecretHash");
        assertThat(ClientResponse.from(loadedClient).toString())
                .doesNotContain(result.clientSecret(), loadedClient.getClientSecretHash());
    }

    @Test
    void clientCreationApiPreservesOneTimeSecretResult() {
        assertThat(ClientApplicationService.class.getDeclaredMethods())
                .filteredOn(method -> method.getName().equals("createClient"))
                .isEmpty();
    }

    @Test
    void createsPublicClientWithoutSecret() {
        Tenant tenant = tenantApplicationService.createTenant("Public Client Tenant");

        ClientSecretResult result = clientApplicationService.createClientWithSecret(
                tenant.getId(),
                "public-portal",
                "Public Portal",
                ClientType.PUBLIC,
                true,
                false,
                Set.of("https://public.example.test/callback"),
                Set.of("authorization_code"),
                Set.of("openid", "profile"),
                Set.of("none"));

        Client loadedClient = clientRepository.findById(result.client().getId()).orElseThrow();
        assertThat(result.clientSecret()).isNull();
        assertThat(loadedClient.getClientType()).isEqualTo(ClientType.PUBLIC);
        assertThat(loadedClient.getClientSecretHash()).isNull();
        assertThat(loadedClient.isRequirePkce()).isTrue();
        assertThat(loadedClient.getAuthenticationMethods()).containsExactly("none");
    }

    @Test
    void updatesClientRegistrationSettings() {
        Tenant tenant = tenantApplicationService.createTenant("Client Update Tenant");
        Client client = clientApplicationService.createClientWithSecret(
                tenant.getId(),
                "managed-client",
                "Managed Client",
                ClientType.CONFIDENTIAL,
                true,
                true,
                Set.of("https://client.example.test/callback"),
                Set.of("authorization_code"),
                Set.of("iam.read"),
                Set.of("client_secret_basic"))
                .client();

        Client updatedClient = clientApplicationService.updateClient(
                client.getId(),
                "Managed Client Updated",
                ClientStatus.DISABLED,
                true,
                false,
                Set.of("https://client.example.test/updated-callback"),
                Set.of("client_credentials"),
                Set.of("iam.write"),
                Set.of("client_secret_post"));

        assertThat(updatedClient.getClientName()).isEqualTo("Managed Client Updated");
        assertThat(updatedClient.getStatus()).isEqualTo(ClientStatus.DISABLED);
        assertThat(updatedClient.isRequireConsent()).isFalse();
        assertThat(updatedClient.getRedirectUris()).containsExactly("https://client.example.test/updated-callback");
        assertThat(updatedClient.getGrantTypes()).containsExactly("client_credentials");
        assertThat(updatedClient.getScopes()).containsExactly("iam.write");
        assertThat(updatedClient.getAuthenticationMethods()).containsExactly("client_secret_post");
        assertThat(auditLogRepository.findByAction("CLIENT_UPDATED"))
                .singleElement()
                .satisfies(auditLog -> assertThat(auditLog.getResourceId()).isEqualTo(client.getId()));
    }

    @Test
    void clientCredentialsClientCanBeCreatedWithoutRedirectUri() {
        Tenant tenant = tenantApplicationService.createTenant("Client Credentials Tenant");

        ClientSecretResult result = clientApplicationService.createClientWithSecret(
                tenant.getId(),
                "machine-client",
                "Machine Client",
                ClientType.CONFIDENTIAL,
                false,
                false,
                null,
                Set.of("client_credentials"),
                Set.of("iam.write"),
                Set.of("client_secret_basic"));

        assertThat(result.clientSecret()).isNotBlank();
        assertThat(result.client().getRedirectUris()).isEmpty();
        assertThat(passwordEncoder.matches(result.clientSecret(), result.client().getClientSecretHash())).isTrue();
    }

    @Test
    void confidentialClientWithoutSecretAuthenticationDoesNotForceSecret() {
        Tenant tenant = tenantApplicationService.createTenant("Confidential Non Secret Tenant");

        ClientSecretResult result = clientApplicationService.createClientWithSecret(
                tenant.getId(),
                "non-secret-confidential-client",
                "Non Secret Confidential Client",
                ClientType.CONFIDENTIAL,
                false,
                false,
                null,
                Set.of("client_credentials"),
                Set.of("iam.read"),
                Set.of("none"));

        assertThat(result.clientSecret()).isNull();
        assertThat(result.client().getClientSecretHash()).isNull();
        assertThat(result.client().getClientType()).isEqualTo(ClientType.CONFIDENTIAL);
    }

    @Test
    void updateDoesNotAllowChangingClientType() {
        assertThat(io.github.doubletree.iam.platform.web.dto.UpdateClientRequest.class.getRecordComponents())
                .extracting(RecordComponent::getName)
                .doesNotContain("clientType");
        assertThat(ClientApplicationService.class.getDeclaredMethods())
                .filteredOn(method -> method.getName().equals("updateClient"))
                .allSatisfy(method -> assertThat(method.getParameterTypes()).doesNotContain(ClientType.class));
    }

    @Test
    void rotatingConfidentialClientSecretChangesStoredHash() {
        Tenant tenant = tenantApplicationService.createTenant("Client Rotation Tenant");
        ClientSecretResult created = clientApplicationService.createClientWithSecret(
                tenant.getId(),
                "rotation-client",
                "Rotation Client",
                ClientType.CONFIDENTIAL,
                true,
                true,
                Set.of("https://rotation.example.test/callback"),
                Set.of("authorization_code"),
                Set.of("iam.read"),
                Set.of("client_secret_basic"));
        String originalHash = created.client().getClientSecretHash();

        ClientSecretResult rotated = clientApplicationService.rotateClientSecret(created.client().getId());

        assertThat(rotated.clientSecret()).isNotBlank().isNotEqualTo(created.clientSecret());
        assertThat(rotated.client().getClientSecretHash()).isNotEqualTo(originalHash);
        assertThat(passwordEncoder.matches(rotated.clientSecret(), rotated.client().getClientSecretHash())).isTrue();
        assertThat(auditLogRepository.findByAction("CLIENT_SECRET_ROTATED"))
                .singleElement()
                .satisfies(auditLog -> assertThat(auditLog.getResourceId()).isEqualTo(created.client().getId()));
    }

    @Test
    void rotatingPublicClientSecretIsRejected() {
        Tenant tenant = tenantApplicationService.createTenant("Public Rotation Tenant");
        Client publicClient = clientApplicationService.createClientWithSecret(
                tenant.getId(),
                "public-rotation-client",
                "Public Rotation Client",
                ClientType.PUBLIC,
                true,
                false,
                Set.of("https://public-rotation.example.test/callback"),
                Set.of("authorization_code"),
                Set.of("openid"),
                Set.of("none"))
                .client();

        assertThatThrownBy(() -> clientApplicationService.rotateClientSecret(publicClient.getId()))
                .isInstanceOf(ClientValidationException.class)
                .hasMessage("Public clients do not have client secrets");
    }

    @Test
    void authorizationCodeClientRequiresRedirectUri() {
        Tenant tenant = tenantApplicationService.createTenant("Authorization Code Redirect Tenant");

        assertThatThrownBy(() -> clientApplicationService.createClientWithSecret(
                        tenant.getId(),
                        "auth-code-null-redirect",
                        "Auth Code Null Redirect",
                        ClientType.CONFIDENTIAL,
                        true,
                        true,
                        null,
                        Set.of("authorization_code"),
                        Set.of("iam.read"),
                        Set.of("client_secret_basic")))
                .isInstanceOf(ClientValidationException.class)
                .hasMessage("Authorization code clients must have at least one redirect URI");
        assertThatThrownBy(() -> clientApplicationService.createClientWithSecret(
                        tenant.getId(),
                        "auth-code-empty-redirect",
                        "Auth Code Empty Redirect",
                        ClientType.CONFIDENTIAL,
                        true,
                        true,
                        Set.of(),
                        Set.of("authorization_code"),
                        Set.of("iam.read"),
                        Set.of("client_secret_basic")))
                .isInstanceOf(ClientValidationException.class)
                .hasMessage("Authorization code clients must have at least one redirect URI");
    }

    @Test
    void clientCollectionMutationBypassIsPrevented() {
        Tenant tenant = tenantApplicationService.createTenant("Client Collection Boundary Tenant");
        Client client = Client.create(tenant, "collection-client", "Collection Client");

        assertThatThrownBy(() -> client.getRedirectUris().add("https://bypass.example.test/callback"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> client.addRedirectUri(" "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Client redirect URI must not be blank");
        client.replaceGrantTypes(Set.of("authorization_code"));
        assertThatThrownBy(client::validateRegistration)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Authorization code clients must have at least one redirect URI");
    }

    @Test
    void permissionCreationRequiresTenantContext() {
        assertThat(Permission.class.getDeclaredMethods())
                .filteredOn(method -> method.getName().equals("create"))
                .allSatisfy(method -> assertThat(method.getParameterTypes()).contains(Tenant.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " "})
    void clientValidationRejectsBlankConfigurationValues(String blankValue) {
        Tenant tenant = tenantApplicationService.createTenant("Invalid Client Tenant " + blankValue.length());

        assertThatThrownBy(() -> clientApplicationService.createClientWithSecret(
                        tenant.getId(),
                        "invalid-redirect-client-" + blankValue.length(),
                        "Invalid Redirect Client",
                        ClientType.CONFIDENTIAL,
                        true,
                        true,
                        Set.of(blankValue),
                        Set.of("authorization_code"),
                        Set.of("iam.read"),
                        Set.of("client_secret_basic")))
                .isInstanceOf(ClientValidationException.class)
                .hasMessage("Client redirect URI must not be blank");
        assertThatThrownBy(() -> clientApplicationService.createClientWithSecret(
                        tenant.getId(),
                        "invalid-grant-client-" + blankValue.length(),
                        "Invalid Grant Client",
                        ClientType.CONFIDENTIAL,
                        true,
                        true,
                        Set.of("https://invalid-grant.example.test/callback"),
                        Set.of(blankValue),
                        Set.of("iam.read"),
                        Set.of("client_secret_basic")))
                .isInstanceOf(ClientValidationException.class)
                .hasMessage("Client grant type must not be blank");
        assertThatThrownBy(() -> clientApplicationService.createClientWithSecret(
                        tenant.getId(),
                        "invalid-scope-client-" + blankValue.length(),
                        "Invalid Scope Client",
                        ClientType.CONFIDENTIAL,
                        true,
                        true,
                        Set.of("https://invalid-scope.example.test/callback"),
                        Set.of("authorization_code"),
                        Set.of(blankValue),
                        Set.of("client_secret_basic")))
                .isInstanceOf(ClientValidationException.class)
                .hasMessage("Client scope must not be blank");
        assertThatThrownBy(() -> clientApplicationService.createClientWithSecret(
                        tenant.getId(),
                        "invalid-auth-client-" + blankValue.length(),
                        "Invalid Auth Client",
                        ClientType.CONFIDENTIAL,
                        true,
                        true,
                        Set.of("https://invalid-auth.example.test/callback"),
                        Set.of("authorization_code"),
                        Set.of("iam.read"),
                        Set.of(blankValue)))
                .isInstanceOf(ClientValidationException.class)
                .hasMessage("Client authentication method must not be blank");
    }

    @Test
    void publicClientValidationRejectsSecretConfigurationAndMissingPkce() {
        Tenant tenant = tenantApplicationService.createTenant("Public Client Tenant");
        Client publicClientWithSecret = Client.create(tenant, "public-with-secret", "Public With Secret");

        assertThatThrownBy(() -> {
                    publicClientWithSecret.setClientType(ClientType.PUBLIC);
                    publicClientWithSecret.setClientSecretHash("secret-hash");
                })
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Public clients must not have a client secret");
        assertThatThrownBy(() -> clientApplicationService.createClientWithSecret(
                        tenant.getId(),
                        "public-no-pkce",
                        "Public No PKCE",
                        ClientType.PUBLIC,
                        false,
                        false,
                        Set.of("https://public-no-pkce.example.test/callback"),
                        Set.of("authorization_code"),
                        Set.of("openid"),
                        Set.of("none")))
                .isInstanceOf(ClientValidationException.class)
                .hasMessage("Public clients must require PKCE");
        assertThatThrownBy(() -> clientApplicationService.createClientWithSecret(
                        tenant.getId(),
                        "public-secret-auth",
                        "Public Secret Auth",
                        ClientType.PUBLIC,
                        true,
                        false,
                        Set.of("https://public-secret-auth.example.test/callback"),
                        Set.of("authorization_code"),
                        Set.of("openid"),
                        Set.of("client_secret_basic")))
                .isInstanceOf(ClientValidationException.class)
                .hasMessage("Public clients must not use client secret authentication");
    }

    @Test
    void confidentialClientUsingSecretAuthenticationRequiresStoredSecretHash() {
        Tenant tenant = tenantApplicationService.createTenant("Confidential Secret Boundary Tenant");
        Client client = Client.create(tenant, "confidential-no-secret", "Confidential No Secret");
        client.setRedirectUris(Set.of("https://confidential-no-secret.example.test/callback"));
        client.setGrantTypes(Set.of("authorization_code"));
        client.setScopes(Set.of("iam.read"));
        client.setAuthenticationMethods(Set.of("client_secret_basic"));

        assertThatThrownBy(client::validateRegistration)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Confidential clients using client secret authentication must have a client secret");
    }

    @Test
    void createsGroupUnderTenant() {
        Tenant tenant = tenantApplicationService.createTenant("Group Tenant");

        Group group = groupApplicationService.createGroup(tenant.getId(), "engineering");

        Group loadedGroup = groupRepository.findById(group.getId()).orElseThrow();
        assertThat(loadedGroup.getTenant().getId()).isEqualTo(tenant.getId());
        assertThat(loadedGroup.getName()).isEqualTo("engineering");
    }

    @Test
    void addsUserToGroupUnderSameTenant() {
        Tenant tenant = tenantApplicationService.createTenant("Group Member Tenant");
        User user = userApplicationService.createUser(tenant.getId(), "member-user", "Member User");
        Group group = groupApplicationService.createGroup(tenant.getId(), "members");

        groupApplicationService.addUserToGroup(group.getId(), user.getId());

        Group loadedGroup = groupRepository.findById(group.getId()).orElseThrow();
        assertThat(loadedGroup.getUsers())
                .extracting(User::getUsername)
                .containsExactly("member-user");
    }

    @Test
    void removesUserFromGroupUnderSameTenant() {
        Tenant tenant = tenantApplicationService.createTenant("Group Remove Tenant");
        User user = userApplicationService.createUser(tenant.getId(), "remove-user", "Remove User");
        Group group = groupApplicationService.createGroup(tenant.getId(), "removable");
        groupApplicationService.addUserToGroup(group.getId(), user.getId());

        groupApplicationService.removeUserFromGroup(group.getId(), user.getId());

        Group loadedGroup = groupRepository.findById(group.getId()).orElseThrow();
        assertThat(loadedGroup.getUsers()).isEmpty();
    }

    @Test
    void rejectsRemovingUserFromDifferentTenantFromGroup() {
        Tenant groupTenant = tenantApplicationService.createTenant("Group Remove Boundary Tenant");
        Tenant userTenant = tenantApplicationService.createTenant("Remove Member Boundary Tenant");
        Group group = groupApplicationService.createGroup(groupTenant.getId(), "remove-boundary-group");
        User user = userApplicationService.createUser(userTenant.getId(), "remove-external-member", "Remove External");

        assertThatThrownBy(() -> groupApplicationService.removeUserFromGroup(group.getId(), user.getId()))
                .isInstanceOf(TenantBoundaryViolationException.class)
                .hasMessage("User and group must belong to the same tenant");
    }

    @Test
    void removingUserWhoIsNotGroupMemberDoesNotRecordRemovalAuditEvent() {
        Tenant tenant = tenantApplicationService.createTenant("No Op Remove Tenant");
        User user = userApplicationService.createUser(tenant.getId(), "not-member-user", "Not Member User");
        Group group = groupApplicationService.createGroup(tenant.getId(), "no-op-removable");

        groupApplicationService.removeUserFromGroup(group.getId(), user.getId());

        Group loadedGroup = groupRepository.findById(group.getId()).orElseThrow();
        assertThat(loadedGroup.getUsers()).isEmpty();
        assertThat(auditLogRepository.findByAction("USER_REMOVED_FROM_GROUP")).isEmpty();
    }

    @Test
    void rejectsAddingUserFromDifferentTenantToGroup() {
        Tenant groupTenant = tenantApplicationService.createTenant("Group Boundary Tenant");
        Tenant userTenant = tenantApplicationService.createTenant("Member Boundary Tenant");
        Group group = groupApplicationService.createGroup(groupTenant.getId(), "boundary-group");
        User user = userApplicationService.createUser(userTenant.getId(), "external-member", "External Member");

        assertThatThrownBy(() -> groupApplicationService.addUserToGroup(group.getId(), user.getId()))
                .isInstanceOf(TenantBoundaryViolationException.class)
                .hasMessage("User and group must belong to the same tenant");

        Group loadedGroup = groupRepository.findById(group.getId()).orElseThrow();
        assertThat(loadedGroup.getUsers()).isEmpty();
    }

    @Test
    void recordsAuditEventsForGroupOperations() {
        Tenant tenant = tenantApplicationService.createTenant("Group Audit Tenant");
        User user = userApplicationService.createUser(tenant.getId(), "group-audit-user", "Group Audit User");
        Group group = groupApplicationService.createGroup(tenant.getId(), "audited-group");

        groupApplicationService.addUserToGroup(group.getId(), user.getId());
        groupApplicationService.removeUserFromGroup(group.getId(), user.getId());

        assertThat(auditLogRepository.findByAction("GROUP_CREATED"))
                .singleElement()
                .satisfies(auditLog -> {
                    assertThat(auditLog.getTenantId()).isEqualTo(tenant.getId());
                    assertThat(auditLog.getResourceType()).isEqualTo("GROUP");
                    assertThat(auditLog.getResourceId()).isEqualTo(group.getId());
                });
        assertThat(auditLogRepository.findByAction("USER_ADDED_TO_GROUP"))
                .singleElement()
                .satisfies(auditLog -> assertThat(auditLog.getResourceId()).isEqualTo(group.getId()));
        assertThat(auditLogRepository.findByAction("USER_REMOVED_FROM_GROUP"))
                .singleElement()
                .satisfies(auditLog -> assertThat(auditLog.getResourceId()).isEqualTo(group.getId()));
    }

    @Test
    void auditLogDoesNotIncludeSensitiveData() {
        Tenant tenant = tenantApplicationService.createTenant("Sensitive Audit Tenant");

        clientApplicationService.createClientWithSecret(
                tenant.getId(),
                "secret-client-id",
                "Client With Secret",
                ClientType.CONFIDENTIAL,
                false,
                false,
                null,
                Set.of("client_credentials"),
                Set.of("iam.read"),
                Set.of("client_secret_basic"));

        assertThat(auditLogRepository.findByAction("CLIENT_CREATED"))
                .singleElement()
                .satisfies(this::assertNoSensitiveData);
    }

    private void assertNoSensitiveData(AuditLog auditLog) {
        assertThat(auditLog.getActorType().name())
                .doesNotContainIgnoringCase("secret")
                .doesNotContainIgnoringCase("token")
                .doesNotContainIgnoringCase("password");
        assertThat(auditLog.getAction())
                .doesNotContainIgnoringCase("secret")
                .doesNotContainIgnoringCase("token")
                .doesNotContainIgnoringCase("password");
        assertThat(auditLog.getResourceType())
                .doesNotContainIgnoringCase("secret")
                .doesNotContainIgnoringCase("token")
                .doesNotContainIgnoringCase("password");
    }

    private void assertAuditLogDoesNotContain(AuditLog auditLog, String sensitiveValue) {
        assertThat(auditLog.getActorType().name()).doesNotContain(sensitiveValue);
        assertThat(auditLog.getAction()).doesNotContain(sensitiveValue);
        assertThat(auditLog.getResourceType()).doesNotContain(sensitiveValue);
        assertThat(auditLog.getResourceId().toString()).doesNotContain(sensitiveValue);
    }
}

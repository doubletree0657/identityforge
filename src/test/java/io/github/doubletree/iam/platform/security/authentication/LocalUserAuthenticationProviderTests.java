package io.github.doubletree.iam.platform.security.authentication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.doubletree.iam.platform.application.service.AuditApplicationService;
import io.github.doubletree.iam.platform.application.service.EffectiveAuthorizationService;
import io.github.doubletree.iam.platform.domain.AccountStatus;
import io.github.doubletree.iam.platform.domain.AuditLog;
import io.github.doubletree.iam.platform.domain.PasswordCredential;
import io.github.doubletree.iam.platform.domain.Tenant;
import io.github.doubletree.iam.platform.domain.User;
import io.github.doubletree.iam.platform.repository.AuditLogRepository;
import io.github.doubletree.iam.platform.repository.TenantRepository;
import io.github.doubletree.iam.platform.repository.UserRepository;
import io.github.doubletree.iam.platform.security.PasswordEncodingConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
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
        LocalUserAuthenticationProvider.class,
        LocalUserAuthenticationConfiguration.class,
        PlatformUserDetailsService.class,
        EffectiveAuthorizationService.class,
        PasswordEncodingConfiguration.class,
        AuditApplicationService.class
})
class LocalUserAuthenticationProviderTests {

    private static final String GENERIC_AUTHENTICATION_FAILURE = "Invalid username or password";

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private AuthenticationManager authenticationManager;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @DynamicPropertySource
    static void registerDataSourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", postgres::getDriverClassName);
    }

    @Test
    void activeUserAuthenticatesWithCorrectPassword() {
        User user = createUser("active-user", "correct-password-123", AccountStatus.ACTIVE);

        Authentication authentication = authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated("active-user", "correct-password-123"));

        assertThat(authentication.isAuthenticated()).isTrue();
        assertThat(authentication.getPrincipal()).isInstanceOf(PlatformUserDetails.class);
        PlatformUserDetails userDetails = (PlatformUserDetails) authentication.getPrincipal();
        assertThat(userDetails.userId()).isEqualTo(user.getId());
        assertThat(userDetails.toString()).doesNotContain(user.getPasswordCredential().getPasswordHash());
        assertThat(authentication.getCredentials()).isNull();
        assertThat(auditLogRepository.findByAction("USER_AUTHENTICATION_SUCCEEDED"))
                .singleElement()
                .satisfies(auditLog -> {
                    assertThat(auditLog.getTenantId()).isEqualTo(user.getTenant().getId());
                    assertThat(auditLog.getResourceType()).isEqualTo("USER");
                    assertThat(auditLog.getResourceId()).isEqualTo(user.getId());
                    assertAuditLogDoesNotContain(auditLog, "correct-password-123");
                    assertAuditLogDoesNotContain(auditLog, user.getPasswordCredential().getPasswordHash());
                });
    }

    @Test
    void wrongPasswordIsRejectedWithGenericMessage() {
        User user = createUser("wrong-password-user", "correct-password-123", AccountStatus.ACTIVE);

        assertAuthenticationFails("wrong-password-user", "wrong-password");

        assertThat(auditLogRepository.findByAction("USER_AUTHENTICATION_FAILED"))
                .singleElement()
                .satisfies(auditLog -> {
                    assertThat(auditLog.getResourceId()).isEqualTo(user.getId());
                    assertAuditLogDoesNotContain(auditLog, "wrong-password");
                    assertAuditLogDoesNotContain(auditLog, user.getPasswordCredential().getPasswordHash());
                });
    }

    @Test
    void missingUserIsRejectedWithGenericMessage() {
        assertAuthenticationFails("missing-user", "any-password");

        assertThat(auditLogRepository.findByAction("USER_AUTHENTICATION_FAILED")).isEmpty();
    }

    @Test
    void userWithoutPasswordCredentialIsRejectedWithGenericMessage() {
        createUserWithoutPassword("no-password-user", AccountStatus.ACTIVE);

        assertAuthenticationFails("no-password-user", "any-password");
    }

    @Test
    void disabledUserIsRejectedWithGenericMessage() {
        createUser("disabled-user", "disabled-password-123", AccountStatus.DISABLED);

        assertAuthenticationFails("disabled-user", "disabled-password-123");
    }

    @Test
    void lockedUserIsRejectedWithGenericMessage() {
        createUser("locked-user", "locked-password-123", AccountStatus.LOCKED);

        assertAuthenticationFails("locked-user", "locked-password-123");
    }

    @Test
    void pendingUserIsRejectedWithGenericMessage() {
        createUser("pending-user", "pending-password-123", AccountStatus.PENDING);

        assertAuthenticationFails("pending-user", "pending-password-123");
    }

    private void assertAuthenticationFails(String username, String password) {
        assertThatThrownBy(() -> authenticationManager.authenticate(
                        UsernamePasswordAuthenticationToken.unauthenticated(username, password)))
                .isInstanceOf(AuthenticationException.class)
                .hasMessage(GENERIC_AUTHENTICATION_FAILURE);
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

    private void assertAuditLogDoesNotContain(AuditLog auditLog, String sensitiveValue) {
        assertThat(auditLog.getActorType().name()).doesNotContain(sensitiveValue);
        assertThat(auditLog.getAction()).doesNotContain(sensitiveValue);
        assertThat(auditLog.getResourceType()).doesNotContain(sensitiveValue);
    }
}

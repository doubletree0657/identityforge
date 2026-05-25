package io.github.doubletree.iam.platform.application.service;

import io.github.doubletree.iam.platform.application.exception.EntityNotFoundException;
import io.github.doubletree.iam.platform.application.exception.PasswordValidationException;
import io.github.doubletree.iam.platform.application.exception.TenantBoundaryViolationException;
import io.github.doubletree.iam.platform.application.exception.ValidationException;
import io.github.doubletree.iam.platform.domain.AccountStatus;
import io.github.doubletree.iam.platform.domain.PasswordCredential;
import io.github.doubletree.iam.platform.domain.Role;
import io.github.doubletree.iam.platform.domain.Tenant;
import io.github.doubletree.iam.platform.domain.User;
import io.github.doubletree.iam.platform.domain.UserAttribute;
import io.github.doubletree.iam.platform.domain.UserAttributeValueType;
import io.github.doubletree.iam.platform.domain.UserProfile;
import io.github.doubletree.iam.platform.repository.RoleRepository;
import io.github.doubletree.iam.platform.repository.TenantRepository;
import io.github.doubletree.iam.platform.repository.UserAttributeRepository;
import io.github.doubletree.iam.platform.repository.UserProfileRepository;
import io.github.doubletree.iam.platform.repository.UserRepository;
import io.github.doubletree.iam.platform.security.AdminAuthorizationService;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserApplicationService {

    private static final int MIN_PASSWORD_LENGTH = 8;

    private final UserRepository userRepository;
    private final TenantRepository tenantRepository;
    private final RoleRepository roleRepository;
    private final UserProfileRepository userProfileRepository;
    private final UserAttributeRepository userAttributeRepository;
    private final AuditApplicationService auditApplicationService;
    private final PasswordEncoder passwordEncoder;
    private final AdminAuthorizationService adminAuthorizationService;

    public UserApplicationService(
            UserRepository userRepository,
            TenantRepository tenantRepository,
            RoleRepository roleRepository,
            UserProfileRepository userProfileRepository,
            UserAttributeRepository userAttributeRepository,
            AuditApplicationService auditApplicationService,
            PasswordEncoder passwordEncoder,
            AdminAuthorizationService adminAuthorizationService) {
        this.userRepository = userRepository;
        this.tenantRepository = tenantRepository;
        this.roleRepository = roleRepository;
        this.userProfileRepository = userProfileRepository;
        this.userAttributeRepository = userAttributeRepository;
        this.auditApplicationService = auditApplicationService;
        this.passwordEncoder = passwordEncoder;
        this.adminAuthorizationService = adminAuthorizationService;
    }

    @Transactional
    public User createUser(UUID tenantId, String username, String displayName) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new EntityNotFoundException("Tenant not found: " + tenantId));
        adminAuthorizationService.assertTenantAccess(tenant.getId());

        User user = userRepository.save(User.create(tenant, username, displayName));
        auditApplicationService.recordEvent(tenant.getId(), "USER_CREATED", "USER", user.getId());
        return user;
    }

    @Transactional(readOnly = true)
    public Page<User> listUsers(UUID tenantId, Pageable pageable) {
        UUID allowedTenantId = adminAuthorizationService.tenantIdForList(tenantId);
        if (allowedTenantId == null) {
            return userRepository.findAll(pageable);
        }
        return userRepository.findByTenantId(allowedTenantId, pageable);
    }

    @Transactional(readOnly = true)
    public User findUser(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("User not found: " + userId));
        adminAuthorizationService.assertTenantAccess(user.getTenant().getId());
        return user;
    }

    @Transactional
    public User updateUser(
            UUID userId,
            String displayName,
            String email,
            Boolean emailVerified,
            String phoneNumber,
            Boolean phoneNumberVerified,
            AccountStatus accountStatus) {
        User user = loadUser(userId);
        AccountStatus previousStatus = user.getAccountStatus();
        if (displayName != null) {
            user.setDisplayName(displayName);
        }
        if (email != null) {
            user.setEmail(email);
        }
        if (emailVerified != null) {
            user.setEmailVerified(emailVerified);
        }
        if (phoneNumber != null) {
            user.setPhoneNumber(phoneNumber);
        }
        if (phoneNumberVerified != null) {
            user.setPhoneNumberVerified(phoneNumberVerified);
        }
        if (accountStatus != null) {
            user.setAccountStatus(accountStatus);
        }
        User savedUser = userRepository.save(user);
        auditApplicationService.recordEvent(savedUser.getTenant().getId(), "USER_UPDATED", "USER", savedUser.getId());
        if (accountStatus != null && accountStatus != previousStatus) {
            auditApplicationService.recordEvent(
                    savedUser.getTenant().getId(), "USER_STATUS_CHANGED", "USER", savedUser.getId());
        }
        return savedUser;
    }

    @Transactional
    public User assignRoleToUser(UUID userId, UUID roleId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("User not found: " + userId));
        Role role = roleRepository.findById(roleId)
                .orElseThrow(() -> new EntityNotFoundException("Role not found: " + roleId));

        adminAuthorizationService.assertSameTenant(
                user.getTenant().getId(), role.getTenant().getId(), "User and role must belong to the same tenant");

        user.getRoles().add(role);
        User savedUser = userRepository.save(user);
        auditApplicationService.recordEvent(
                savedUser.getTenant().getId(), "ROLE_ASSIGNED_TO_USER", "USER", savedUser.getId());
        return savedUser;
    }

    @Transactional
    public User removeRoleFromUser(UUID userId, UUID roleId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("User not found: " + userId));
        Role role = roleRepository.findById(roleId)
                .orElseThrow(() -> new EntityNotFoundException("Role not found: " + roleId));

        adminAuthorizationService.assertSameTenant(
                user.getTenant().getId(), role.getTenant().getId(), "User and role must belong to the same tenant");

        boolean removed = user.getRoles().remove(role);
        User savedUser = userRepository.save(user);
        if (removed) {
            auditApplicationService.recordEvent(
                    savedUser.getTenant().getId(), "ROLE_REMOVED_FROM_USER", "USER", savedUser.getId());
        }
        return savedUser;
    }

    @Transactional(readOnly = true)
    public UserProfile findProfileByUserId(UUID userId) {
        User user = loadUser(userId);
        return userProfileRepository.findByUserId(userId)
                .orElseGet(() -> UserProfile.create(user));
    }

    @Transactional
    public UserProfile updateProfile(
            UUID userId,
            String givenName,
            String familyName,
            String preferredName,
            String locale,
            String timezone,
            String avatarUrl,
            String jobTitle,
            String department,
            String organization,
            String employeeNumber) {
        User user = loadUser(userId);
        UserProfile profile = userProfileRepository.findByUserId(userId)
                .orElseGet(() -> UserProfile.create(user));
        profile.setGivenName(givenName);
        profile.setFamilyName(familyName);
        profile.setPreferredName(preferredName);
        profile.setLocale(locale);
        profile.setTimezone(timezone);
        profile.setAvatarUrl(avatarUrl);
        profile.setJobTitle(jobTitle);
        profile.setDepartment(department);
        profile.setOrganization(organization);
        profile.setEmployeeNumber(employeeNumber);
        UserProfile savedProfile = userProfileRepository.save(profile);
        auditApplicationService.recordEvent(user.getTenant().getId(), "USER_PROFILE_UPDATED", "USER", user.getId());
        return savedProfile;
    }

    @Transactional(readOnly = true)
    public List<UserAttribute> listAttributes(UUID userId) {
        ensureUserExists(userId);
        return userAttributeRepository.findByUserIdOrderByNameAsc(userId);
    }

    @Transactional
    public UserAttribute setAttribute(UUID userId, String name, String value, UserAttributeValueType valueType) {
        User user = loadUser(userId);
        validateAttribute(name, value, valueType);
        UserAttribute attribute = userAttributeRepository.findByUserIdAndName(userId, name)
                .orElseGet(() -> UserAttribute.create(user, name, value, valueType));
        attribute.setValue(value);
        attribute.setValueType(valueType);
        UserAttribute savedAttribute = userAttributeRepository.save(attribute);
        auditApplicationService.recordEvent(user.getTenant().getId(), "USER_ATTRIBUTE_SET", "USER", user.getId());
        return savedAttribute;
    }

    @Transactional
    public void deleteAttribute(UUID userId, String name) {
        User user = loadUser(userId);
        userAttributeRepository.deleteByUserIdAndName(userId, name);
        auditApplicationService.recordEvent(user.getTenant().getId(), "USER_ATTRIBUTE_DELETED", "USER", user.getId());
    }

    @Transactional
    public User setInitialPassword(UUID userId, String rawPassword) {
        User user = loadUser(userId);
        applyPasswordChange(user, rawPassword);
        user.ensurePasswordCredential().setPasswordResetRequired(false);
        user.setAccountStatus(AccountStatus.ACTIVE);
        User savedUser = userRepository.save(user);
        auditApplicationService.recordEvent(
                savedUser.getTenant().getId(), "USER_PASSWORD_SET", "USER", savedUser.getId());
        return savedUser;
    }

    @Transactional
    public User updatePassword(UUID userId, String rawPassword) {
        User user = loadUser(userId);
        applyPasswordChange(user, rawPassword);
        user.ensurePasswordCredential().setPasswordResetRequired(false);
        User savedUser = userRepository.save(user);
        auditApplicationService.recordEvent(
                savedUser.getTenant().getId(), "USER_PASSWORD_UPDATED", "USER", savedUser.getId());
        return savedUser;
    }

    @Transactional
    public User requirePasswordReset(UUID userId) {
        User user = loadUser(userId);
        user.ensurePasswordCredential().setPasswordResetRequired(true);
        User savedUser = userRepository.save(user);
        auditApplicationService.recordEvent(
                savedUser.getTenant().getId(), "USER_PASSWORD_RESET_REQUIRED", "USER", savedUser.getId());
        return savedUser;
    }

    @Transactional
    public User clearPasswordResetRequired(UUID userId) {
        User user = loadUser(userId);
        user.ensurePasswordCredential().setPasswordResetRequired(false);
        User savedUser = userRepository.save(user);
        auditApplicationService.recordEvent(
                savedUser.getTenant().getId(), "USER_PASSWORD_RESET_CLEARED", "USER", savedUser.getId());
        return savedUser;
    }

    private User loadUser(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("User not found: " + userId));
        adminAuthorizationService.assertTenantAccess(user.getTenant().getId());
        return user;
    }

    private void ensureUserExists(UUID userId) {
        if (!userRepository.existsById(userId)) {
            throw new EntityNotFoundException("User not found: " + userId);
        }
    }

    private void applyPasswordChange(User user, String rawPassword) {
        validatePassword(rawPassword);
        PasswordCredential credential = user.ensurePasswordCredential();
        credential.setPasswordHash(passwordEncoder.encode(rawPassword));
        credential.setPasswordUpdatedAt(Instant.now());
        credential.setCredentialsVersion(credential.getCredentialsVersion() + 1);
    }

    private void validatePassword(String rawPassword) {
        if (rawPassword == null) {
            throw new PasswordValidationException("Password must be provided");
        }
        if (rawPassword.isBlank()) {
            throw new PasswordValidationException("Password must not be blank");
        }
        if (rawPassword.length() < MIN_PASSWORD_LENGTH) {
            throw new PasswordValidationException(
                    "Password must be at least " + MIN_PASSWORD_LENGTH + " characters");
        }
    }

    private void validateAttribute(String name, String value, UserAttributeValueType valueType) {
        if (name == null || name.isBlank()) {
            throw new ValidationException("Attribute name must be provided");
        }
        if (value == null) {
            throw new ValidationException("Attribute value must be provided");
        }
        if (valueType == null) {
            throw new ValidationException("Attribute value type must be provided");
        }
        String normalizedName = name.toLowerCase(Locale.ROOT);
        if (normalizedName.contains("password")
                || normalizedName.contains("secret")
                || normalizedName.contains("token")
                || normalizedName.contains("credential")) {
            throw new ValidationException("Secret-like values must not be stored as user attributes");
        }
    }
}

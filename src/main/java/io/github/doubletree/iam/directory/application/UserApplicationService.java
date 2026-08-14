package io.github.doubletree.iam.directory.application;
import io.github.doubletree.iam.audit.application.AuditApplicationService;

import io.github.doubletree.iam.shared.exception.EntityNotFoundException;
import io.github.doubletree.iam.shared.exception.PasswordValidationException;
import io.github.doubletree.iam.shared.exception.TenantBoundaryViolationException;
import io.github.doubletree.iam.shared.exception.ValidationException;
import io.github.doubletree.iam.directory.domain.AccountStatus;
import io.github.doubletree.iam.directory.domain.PasswordCredential;
import io.github.doubletree.iam.directory.domain.Role;
import io.github.doubletree.iam.directory.domain.Tenant;
import io.github.doubletree.iam.directory.domain.User;
import io.github.doubletree.iam.directory.domain.UserAttribute;
import io.github.doubletree.iam.directory.domain.UserAttributeValueType;
import io.github.doubletree.iam.directory.domain.UserProfile;
import io.github.doubletree.iam.directory.domain.IdentityAttributePolicy;
import io.github.doubletree.iam.directory.domain.TenantStatus;
import io.github.doubletree.iam.directory.infrastructure.persistence.RoleRepository;
import io.github.doubletree.iam.directory.infrastructure.persistence.TenantRepository;
import io.github.doubletree.iam.directory.infrastructure.persistence.UserAttributeRepository;
import io.github.doubletree.iam.directory.infrastructure.persistence.UserProfileRepository;
import io.github.doubletree.iam.directory.infrastructure.persistence.UserRepository;
import io.github.doubletree.iam.directory.access.application.AdminAuthorizationService;
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
        if (tenant.getStatus() != TenantStatus.ACTIVE) {
            throw new ValidationException("Users cannot be created in an inactive tenant");
        }

        User candidate;
        try {
            candidate = User.create(tenant, username, displayName);
        } catch (IllegalArgumentException exception) {
            throw new ValidationException(exception.getMessage());
        }
        if (userRepository.findByTenantIdAndNormalizedUsername(
                        tenantId, candidate.getNormalizedUsername()).isPresent()) {
            throw new ValidationException("Username already exists in tenant");
        }
        User user = userRepository.save(candidate);
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
    public Page<User> listUsersByUsername(UUID tenantId, String username, Pageable pageable) {
        UUID allowedTenantId = requireTenantForProvisioning(tenantId);
        return userRepository.findByTenantIdAndNormalizedUsername(
                allowedTenantId,
                io.github.doubletree.iam.directory.domain.UsernameNormalizer.normalize(username),
                pageable);
    }

    @Transactional(readOnly = true)
    public Page<User> listUsersByDisplayName(UUID tenantId, String displayName, Pageable pageable) {
        return userRepository.findByTenantIdAndDisplayNameIgnoreCase(
                requireTenantForProvisioning(tenantId), displayName, pageable);
    }

    @Transactional(readOnly = true)
    public Page<User> listUsersByEmail(UUID tenantId, String email, Pageable pageable) {
        return userRepository.findByTenantIdAndEmailIgnoreCase(
                requireTenantForProvisioning(tenantId),
                identityValue(() -> IdentityAttributePolicy.normalizeEmail(email)),
                pageable);
    }

    @Transactional(readOnly = true)
    public Page<User> listUsersByActive(UUID tenantId, boolean active, Pageable pageable) {
        UUID allowedTenantId = requireTenantForProvisioning(tenantId);
        return active
                ? userRepository.findByTenantIdAndAccountStatus(allowedTenantId, AccountStatus.ACTIVE, pageable)
                : userRepository.findByTenantIdAndAccountStatusNot(allowedTenantId, AccountStatus.ACTIVE, pageable);
    }

    @Transactional(readOnly = true)
    public User findUser(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("User not found: " + userId));
        adminAuthorizationService.assertTenantAccess(user.getTenant().getId());
        return user;
    }

    @Transactional(readOnly = true)
    public User findUser(UUID tenantId, UUID userId) {
        User user = findUser(userId);
        adminAuthorizationService.assertSameTenant(
                tenantId, user.getTenant().getId(), "User does not belong to the requested tenant");
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
            user.setEmail(identityValue(() -> IdentityAttributePolicy.normalizeEmail(email)));
        }
        if (emailVerified != null) {
            user.setEmailVerified(emailVerified);
        }
        if (phoneNumber != null) {
            user.setPhoneNumber(identityValue(() -> IdentityAttributePolicy.validatePhoneNumber(phoneNumber)));
        }
        if (phoneNumberVerified != null) {
            user.setPhoneNumberVerified(phoneNumberVerified);
        }
        if (accountStatus != null) {
            user.setAccountStatus(accountStatus);
            if (accountStatus != previousStatus) {
                incrementSecurityVersion(user);
            }
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
    public User replaceUser(
            UUID tenantId,
            UUID userId,
            String username,
            String displayName,
            String email,
            AccountStatus accountStatus) {
        User user = findUser(tenantId, userId);
        String normalizedUsername;
        try {
            normalizedUsername = io.github.doubletree.iam.directory.domain.UsernameNormalizer.normalize(username);
        } catch (IllegalArgumentException exception) {
            throw new ValidationException(exception.getMessage());
        }
        userRepository.findByTenantIdAndNormalizedUsername(tenantId, normalizedUsername)
                .filter(existing -> !existing.getId().equals(userId))
                .ifPresent(existing -> {
                    throw new ValidationException("Username already exists in tenant");
                });
        AccountStatus previousStatus = user.getAccountStatus();
        boolean usernameChanged = !user.getNormalizedUsername().equals(normalizedUsername);
        user.setUsername(username);
        user.setDisplayName(displayName);
        user.setEmail(email == null || email.isBlank()
                ? null
                : identityValue(() -> IdentityAttributePolicy.normalizeEmail(email)));
        user.setAccountStatus(accountStatus);
        if (usernameChanged || accountStatus != previousStatus) {
            incrementSecurityVersion(user);
        }
        User savedUser = userRepository.save(user);
        auditApplicationService.recordEvent(tenantId, "USER_UPDATED", "USER", savedUser.getId());
        if (accountStatus != previousStatus) {
            auditApplicationService.recordEvent(tenantId, "USER_STATUS_CHANGED", "USER", savedUser.getId());
        }
        return savedUser;
    }

    @Transactional
    public void deleteUser(UUID tenantId, UUID userId) {
        User user = findUser(tenantId, userId);
        userRepository.delete(user);
        auditApplicationService.recordEvent(tenantId, "USER_DELETED", "USER", userId);
    }

    @Transactional
    public User assignRoleToUser(UUID userId, UUID roleId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("User not found: " + userId));
        Role role = roleRepository.findById(roleId)
                .orElseThrow(() -> new EntityNotFoundException("Role not found: " + roleId));

        adminAuthorizationService.assertSameTenant(
                user.getTenant().getId(), role.getTenant().getId(), "User and role must belong to the same tenant");
        adminAuthorizationService.assertMayDelegateRole(role);

        user.getRoles().add(role);
        incrementSecurityVersion(user);
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
        if (removed) {
            incrementSecurityVersion(user);
        }
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
        profile.setLocale(identityValue(() -> IdentityAttributePolicy.validateLocale(locale)));
        profile.setTimezone(identityValue(() -> IdentityAttributePolicy.validateTimezone(timezone)));
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
        loadUser(userId);
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
        incrementSecurityVersion(user);
        User savedUser = userRepository.save(user);
        auditApplicationService.recordEvent(
                savedUser.getTenant().getId(), "USER_PASSWORD_RESET_REQUIRED", "USER", savedUser.getId());
        return savedUser;
    }

    @Transactional
    public User clearPasswordResetRequired(UUID userId) {
        User user = loadUser(userId);
        user.ensurePasswordCredential().setPasswordResetRequired(false);
        incrementSecurityVersion(user);
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

    private UUID requireTenantForProvisioning(UUID tenantId) {
        UUID allowedTenantId = adminAuthorizationService.tenantIdForList(tenantId);
        if (allowedTenantId == null) {
            throw new ValidationException("A tenant is required for provisioning queries");
        }
        return allowedTenantId;
    }

    private void applyPasswordChange(User user, String rawPassword) {
        validatePassword(rawPassword);
        PasswordCredential credential = user.ensurePasswordCredential();
        credential.setPasswordHash(passwordEncoder.encode(rawPassword));
        credential.setPasswordUpdatedAt(Instant.now());
        credential.setCredentialsVersion(credential.getCredentialsVersion() + 1);
    }

    private void incrementSecurityVersion(User user) {
        PasswordCredential credential = user.ensurePasswordCredential();
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

    private String identityValue(java.util.function.Supplier<String> validator) {
        try {
            return validator.get();
        } catch (IllegalArgumentException exception) {
            throw new ValidationException(exception.getMessage());
        }
    }
}

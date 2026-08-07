package io.github.doubletree.iam.authentication.application;

import io.github.doubletree.iam.directory.domain.AccountStatus;
import io.github.doubletree.iam.directory.domain.TenantStatus;
import io.github.doubletree.iam.directory.infrastructure.persistence.UserRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserSecurityStateService {

    private final UserRepository userRepository;

    public UserSecurityStateService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public boolean isTokenStateCurrent(UUID userId, int securityVersion) {
        return userRepository.findById(userId)
                .filter(user -> user.getTenant().getStatus() == TenantStatus.ACTIVE)
                .filter(user -> user.getAccountStatus() == AccountStatus.ACTIVE)
                .filter(user -> user.getPasswordCredential() != null)
                .filter(user -> !user.getPasswordCredential().isPasswordResetRequired())
                .map(user -> user.getPasswordCredential().getCredentialsVersion() == securityVersion)
                .orElse(false);
    }
}

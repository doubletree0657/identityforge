package io.github.doubletree.iam.platform.security.authentication;

import io.github.doubletree.iam.platform.application.service.EffectiveAuthorizationService;
import io.github.doubletree.iam.platform.domain.User;
import io.github.doubletree.iam.platform.repository.UserRepository;
import java.util.List;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PlatformUserDetailsService implements UserDetailsService {

    private static final String GENERIC_AUTHENTICATION_FAILURE = "Invalid username or password";

    private final UserRepository userRepository;
    private final EffectiveAuthorizationService effectiveAuthorizationService;

    public PlatformUserDetailsService(
            UserRepository userRepository,
            EffectiveAuthorizationService effectiveAuthorizationService) {
        this.userRepository = userRepository;
        this.effectiveAuthorizationService = effectiveAuthorizationService;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) {
        List<User> users = userRepository.findByUsername(username);
        if (users.size() != 1) {
            throw new UsernameNotFoundException(GENERIC_AUTHENTICATION_FAILURE);
        }
        User user = users.getFirst();
        return PlatformUserDetails.from(
                userRepository.findByTenantIdAndUsername(user.getTenant().getId(), username).orElse(user),
                effectiveAuthorizationService);
    }
}

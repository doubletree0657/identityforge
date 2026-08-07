package io.github.doubletree.iam.authentication.infrastructure;

import io.github.doubletree.iam.directory.application.EffectiveAuthorizationService;
import io.github.doubletree.iam.directory.domain.User;
import io.github.doubletree.iam.directory.access.application.PlatformAuthorityService;
import io.github.doubletree.iam.authentication.domain.RealmLoginIdentifier;
import io.github.doubletree.iam.directory.infrastructure.persistence.UserRepository;
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
    private final PlatformAuthorityService platformAuthorityService;

    public PlatformUserDetailsService(
            UserRepository userRepository,
            EffectiveAuthorizationService effectiveAuthorizationService,
            PlatformAuthorityService platformAuthorityService) {
        this.userRepository = userRepository;
        this.effectiveAuthorizationService = effectiveAuthorizationService;
        this.platformAuthorityService = platformAuthorityService;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) {
        RealmLoginIdentifier identifier;
        try {
            identifier = RealmLoginIdentifier.parse(username);
        } catch (IllegalArgumentException exception) {
            throw new UsernameNotFoundException(GENERIC_AUTHENTICATION_FAILURE);
        }
        User user = userRepository.findByTenantSlugAndNormalizedUsername(
                        identifier.realm(), identifier.normalizedUsername())
                .orElseThrow(() -> new UsernameNotFoundException(GENERIC_AUTHENTICATION_FAILURE));
        return PlatformUserDetails.from(user, effectiveAuthorizationService,
                platformAuthorityService.isPlatformOperator(user.getId()));
    }
}

package io.github.doubletree.iam.authentication.infrastructure;

import io.github.doubletree.iam.audit.application.AuditApplicationService;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class LocalUserAuthenticationProvider implements AuthenticationProvider {

    static final String GENERIC_AUTHENTICATION_FAILURE = "Invalid username or password";

    private final PlatformUserDetailsService userDetailsService;
    private final PasswordEncoder passwordEncoder;
    private final AuditApplicationService auditApplicationService;

    public LocalUserAuthenticationProvider(
            PlatformUserDetailsService userDetailsService,
            PasswordEncoder passwordEncoder,
            AuditApplicationService auditApplicationService) {
        this.userDetailsService = userDetailsService;
        this.passwordEncoder = passwordEncoder;
        this.auditApplicationService = auditApplicationService;
    }

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        PlatformUserDetails userDetails = loadUser(authentication.getName());
        String rawPassword = authentication.getCredentials() == null
                ? ""
                : authentication.getCredentials().toString();

        if (!canAuthenticate(userDetails)) {
            auditApplicationService.recordFailure(
                    userDetails.tenantId(),
                    "USER_AUTHENTICATION_BLOCKED",
                    "USER",
                    userDetails.userId(),
                    blockedReason(userDetails));
            if (!userDetails.isAccountNonLocked()) {
                throw new LockedException(GENERIC_AUTHENTICATION_FAILURE);
            }
            if (!userDetails.isCredentialsNonExpired()) {
                throw new org.springframework.security.authentication.CredentialsExpiredException(
                        GENERIC_AUTHENTICATION_FAILURE);
            }
            throw new DisabledException(GENERIC_AUTHENTICATION_FAILURE);
        }

        if (!passwordMatches(rawPassword, userDetails.password())) {
            auditAuthenticationFailure(userDetails);
            throw new BadCredentialsException(GENERIC_AUTHENTICATION_FAILURE);
        }

        auditApplicationService.recordEvent(
                userDetails.tenantId(), "USER_AUTHENTICATION_SUCCEEDED", "USER", userDetails.userId());
        UsernamePasswordAuthenticationToken result = UsernamePasswordAuthenticationToken.authenticated(
                userDetails, null, userDetails.getAuthorities());
        result.setDetails(authentication.getDetails());
        return result;
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return UsernamePasswordAuthenticationToken.class.isAssignableFrom(authentication);
    }

    private PlatformUserDetails loadUser(String username) {
        try {
            return (PlatformUserDetails) userDetailsService.loadUserByUsername(username);
        } catch (UsernameNotFoundException exception) {
            throw new BadCredentialsException(GENERIC_AUTHENTICATION_FAILURE);
        }
    }

    private boolean canAuthenticate(PlatformUserDetails userDetails) {
        return userDetails.isEnabled() && userDetails.isAccountNonLocked();
    }

    private boolean passwordMatches(String rawPassword, String passwordHash) {
        if (!StringUtils.hasText(passwordHash)) {
            return false;
        }
        try {
            return passwordEncoder.matches(rawPassword, passwordHash);
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private void auditAuthenticationFailure(PlatformUserDetails userDetails) {
        auditApplicationService.recordFailure(
                userDetails.tenantId(),
                "USER_AUTHENTICATION_FAILED",
                "USER",
                userDetails.userId(),
                "INVALID_CREDENTIALS");
    }

    private String blockedReason(PlatformUserDetails userDetails) {
        if (!userDetails.isAccountNonLocked()) {
            return "ACCOUNT_LOCKED";
        }
        if (!userDetails.isCredentialsNonExpired()) {
            return "PASSWORD_RESET_REQUIRED";
        }
        return "ACCOUNT_OR_TENANT_INACTIVE";
    }
}

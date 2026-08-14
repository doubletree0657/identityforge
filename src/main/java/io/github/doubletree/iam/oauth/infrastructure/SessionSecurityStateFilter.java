package io.github.doubletree.iam.oauth.infrastructure;

import io.github.doubletree.iam.audit.application.AuditApplicationService;
import io.github.doubletree.iam.authentication.application.UserSecurityStateService;
import io.github.doubletree.iam.authentication.infrastructure.PlatformUserDetails;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.web.filter.OncePerRequestFilter;

/** Rejects browser sessions whose absolute lifetime or persisted user state is stale. */
public class SessionSecurityStateFilter extends OncePerRequestFilter {

    private final UserSecurityStateService securityStateService;
    private final AuditApplicationService auditApplicationService;
    private final Duration absoluteTimeout;
    private final SecurityContextLogoutHandler logoutHandler = new SecurityContextLogoutHandler();

    public SessionSecurityStateFilter(
            UserSecurityStateService securityStateService,
            AuditApplicationService auditApplicationService,
            Duration absoluteTimeout) {
        this.securityStateService = securityStateService;
        this.auditApplicationService = auditApplicationService;
        this.absoluteTimeout = absoluteTimeout;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof PlatformUserDetails userDetails)) {
            filterChain.doFilter(request, response);
            return;
        }

        String reason = invalidationReason(request.getSession(false), userDetails);
        if (reason == null) {
            filterChain.doFilter(request, response);
            return;
        }

        auditApplicationService.recordFailure(
                userDetails.tenantId(), "USER_SESSION_REJECTED", "USER", userDetails.userId(), reason);
        logoutHandler.logout(request, response, authentication);
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
    }

    private String invalidationReason(HttpSession session, PlatformUserDetails userDetails) {
        if (session == null) {
            return null;
        }
        if (!absoluteTimeout.isZero() && !absoluteTimeout.isNegative()
                && Instant.ofEpochMilli(session.getCreationTime()).plus(absoluteTimeout).isBefore(Instant.now())) {
            return "SESSION_ABSOLUTE_TIMEOUT";
        }
        return securityStateService.isTokenStateCurrent(userDetails.userId(), userDetails.securityVersion())
                ? null
                : "SECURITY_STATE_CHANGED";
    }
}

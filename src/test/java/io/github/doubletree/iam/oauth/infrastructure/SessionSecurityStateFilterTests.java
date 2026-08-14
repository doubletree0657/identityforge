package io.github.doubletree.iam.oauth.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import io.github.doubletree.iam.audit.application.AuditApplicationService;
import io.github.doubletree.iam.authentication.application.UserSecurityStateService;
import io.github.doubletree.iam.authentication.infrastructure.PlatformUserDetails;
import io.github.doubletree.iam.directory.domain.AccountStatus;
import java.time.Duration;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class SessionSecurityStateFilterTests {

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void absoluteTimeoutInvalidatesAndAuditsTheSession() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        PlatformUserDetails principal = new PlatformUserDetails(
                userId,
                tenantId,
                "user",
                "User",
                null,
                AccountStatus.ACTIVE,
                Set.of(),
                Set.of());
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(principal, null, Set.of()));
        AuditApplicationService auditService = mock(AuditApplicationService.class);
        SessionSecurityStateFilter filter = new SessionSecurityStateFilter(
                mock(UserSecurityStateService.class), auditService, Duration.ofNanos(1));
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpSession session = (MockHttpSession) request.getSession(true);

        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        assertThat(session.isInvalid()).isTrue();
        verify(auditService).recordFailure(
                tenantId, "USER_SESSION_REJECTED", "USER", userId, "SESSION_ABSOLUTE_TIMEOUT");
    }
}

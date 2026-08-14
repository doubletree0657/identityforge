package io.github.doubletree.iam.authentication.infrastructure;

import io.github.doubletree.iam.authentication.application.MfaApplicationService;
import io.github.doubletree.iam.oauth.infrastructure.AuthenticatedSessionLifetime;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;
import org.springframework.security.web.savedrequest.RequestCache;
import org.springframework.security.web.savedrequest.SavedRequest;
import org.springframework.stereotype.Component;

@Component
public class MfaAuthenticationSuccessHandler implements AuthenticationSuccessHandler {

    public static final String PENDING_AUTHENTICATION_ATTRIBUTE = "IAM_PENDING_MFA_AUTHENTICATION";
    public static final String PENDING_TARGET_URL_ATTRIBUTE = "IAM_PENDING_MFA_TARGET_URL";

    private final MfaApplicationService mfaApplicationService;
    private final RequestCache requestCache = new HttpSessionRequestCache();
    private final SavedRequestAwareAuthenticationSuccessHandler delegate = new SavedRequestAwareAuthenticationSuccessHandler();

    public MfaAuthenticationSuccessHandler(MfaApplicationService mfaApplicationService) {
        this.mfaApplicationService = mfaApplicationService;
    }

    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication) throws IOException, ServletException {
        if (authentication.getPrincipal() instanceof PlatformUserDetails userDetails
                && mfaApplicationService.requiresTotpChallenge(userDetails.userId())) {
            HttpSession session = request.getSession();
            SavedRequest savedRequest = requestCache.getRequest(request, response);
            session.setAttribute(PENDING_AUTHENTICATION_ATTRIBUTE, authentication);
            if (savedRequest != null) {
                session.setAttribute(PENDING_TARGET_URL_ATTRIBUTE, savedRequest.getRedirectUrl());
            }
            SecurityContextHolder.clearContext();
            session.removeAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
            response.sendRedirect("/login/mfa");
            return;
        }

        AuthenticatedSessionLifetime.markAuthenticated(request.getSession());
        delegate.onAuthenticationSuccess(request, response, authentication);
    }
}

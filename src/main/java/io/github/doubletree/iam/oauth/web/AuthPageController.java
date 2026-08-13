package io.github.doubletree.iam.oauth.web;

import io.github.doubletree.iam.authentication.application.MfaApplicationService;
import io.github.doubletree.iam.applications.domain.ResourcePermission;
import io.github.doubletree.iam.applications.infrastructure.persistence.ClientRepository;
import io.github.doubletree.iam.authentication.infrastructure.MfaAuthenticationSuccessHandler;
import io.github.doubletree.iam.authentication.infrastructure.PlatformUserDetails;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class AuthPageController {

    private static final String GENERIC_LOGIN_FAILURE = "Invalid username or password";
    private static final String GENERIC_MFA_FAILURE = "Invalid verification code";

    private final MfaApplicationService mfaApplicationService;
    private final RegisteredClientRepository registeredClientRepository;
    private final ObjectProvider<ClientRepository> clientRepository;
    private final HttpSessionSecurityContextRepository securityContextRepository = new HttpSessionSecurityContextRepository();

    public AuthPageController(
            MfaApplicationService mfaApplicationService,
            RegisteredClientRepository registeredClientRepository,
            ObjectProvider<ClientRepository> clientRepository) {
        this.mfaApplicationService = mfaApplicationService;
        this.registeredClientRepository = registeredClientRepository;
        this.clientRepository = clientRepository;
    }

    @GetMapping(value = "/login", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> login(
            @RequestParam(required = false) String error,
            @RequestParam(required = false) String logout,
            HttpServletRequest request) {
        String message = error == null ? "" : GENERIC_LOGIN_FAILURE;
        String logoutMessage = logout == null ? "" : "You have been signed out.";
        return html("""
                <h1>IAM Sign In</h1>
                <p class="lede">Use your tenant realm and local username to continue the OAuth2 authorization flow.</p>
                %s
                %s
                <form method="post" action="/login">
                  %s
                  <label>Realm / username<input name="username" autocomplete="username" placeholder="development/admin" required autofocus></label>
                  <label>Password<input name="password" type="password" autocomplete="current-password" required></label>
                  <button type="submit">Sign in</button>
                </form>
                """.formatted(alert(message), notice(logoutMessage), csrfInput(request)));
    }

    @GetMapping(value = "/login/mfa", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> mfa(HttpServletRequest request, @RequestParam(required = false) String error) {
        if (pendingAuthentication(request.getSession(false)) == null) {
            return redirect("/login");
        }
        return html("""
                <h1>MFA Verification</h1>
                <p class="lede">Enter the six-digit code from your authenticator app, or one of your one-time recovery codes.</p>
                %s
                <form method="post" action="/login/mfa">
                  %s
                  <label>Authenticator or recovery code<input name="code" autocomplete="one-time-code" required autofocus></label>
                  <button type="submit">Verify</button>
                </form>
                """.formatted(alert(error == null ? "" : GENERIC_MFA_FAILURE), csrfInput(request)));
    }

    @PostMapping(value = "/login/mfa", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ResponseEntity<String> verifyMfa(
            @RequestParam String code,
            HttpServletRequest request,
            jakarta.servlet.http.HttpServletResponse response) {
        HttpSession session = request.getSession(false);
        Authentication authentication = pendingAuthentication(session);
        if (authentication == null || !(authentication.getPrincipal() instanceof PlatformUserDetails userDetails)) {
            return redirect("/login");
        }

        if (!mfaApplicationService.verifyTotpChallenge(userDetails.userId(), code)) {
            return redirect("/login/mfa?error");
        }

        SecurityContext securityContext = SecurityContextHolder.createEmptyContext();
        securityContext.setAuthentication(authentication);
        SecurityContextHolder.setContext(securityContext);
        securityContextRepository.saveContext(securityContext, request, response);
        session.removeAttribute(MfaAuthenticationSuccessHandler.PENDING_AUTHENTICATION_ATTRIBUTE);
        Object targetUrl = session.getAttribute(MfaAuthenticationSuccessHandler.PENDING_TARGET_URL_ATTRIBUTE);
        session.removeAttribute(MfaAuthenticationSuccessHandler.PENDING_TARGET_URL_ATTRIBUTE);
        return redirect(targetUrl instanceof String url ? url : "/");
    }

    @GetMapping(value = "/oauth2/consent", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> consent(
            @RequestParam(OAuth2ParameterNames.CLIENT_ID) String clientId,
            @RequestParam(OAuth2ParameterNames.SCOPE) String scope,
            @RequestParam(OAuth2ParameterNames.STATE) String state,
            Authentication authentication,
            HttpServletRequest request) {
        RegisteredClient client = registeredClientRepository.findByClientId(clientId);
        String clientName = client == null ? clientId : client.getClientName();
        Map<String, String> scopeDescriptions = scopeDescriptions(scope, client);
        String scopeInputs = scopeDescriptions.keySet().stream()
                .map(value -> "<input type=\"hidden\" name=\"scope\" value=\"" + escape(value) + "\">")
                .collect(Collectors.joining("\n"));
        String scopeList = scopeDescriptions.entrySet().stream()
                .map(entry -> "<li><strong>" + escape(entry.getKey()) + "</strong><span>" + escape(entry.getValue()) + "</span></li>")
                .collect(Collectors.joining("\n"));

        return html("""
                <h1>Authorize %s</h1>
                <p class="lede">Signed in as <strong>%s</strong>. Review the requested access before continuing.</p>
                <ul class="scopes">%s</ul>
                <form method="post" action="/oauth2/authorize" class="actions">
                  %s
                  <input type="hidden" name="client_id" value="%s">
                  <input type="hidden" name="state" value="%s">
                  %s
                  <button type="submit">Approve</button>
                </form>
                <form method="post" action="/oauth2/authorize" class="actions secondary">
                  %s
                  <input type="hidden" name="client_id" value="%s">
                  <input type="hidden" name="state" value="%s">
                  <button type="submit">Deny</button>
                </form>
                """.formatted(
                escape(clientName),
                escape(authentication == null ? "unknown" : authentication.getName()),
                scopeList,
                csrfInput(request),
                escape(clientId),
                escape(state),
                scopeInputs,
                csrfInput(request),
                escape(clientId),
                escape(state)));
    }

    @GetMapping(value = "/logout-success", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> logoutSuccess() {
        return redirect("/login?logout");
    }

    private Authentication pendingAuthentication(HttpSession session) {
        if (session == null) {
            return null;
        }
        Object pending = session.getAttribute(MfaAuthenticationSuccessHandler.PENDING_AUTHENTICATION_ATTRIBUTE);
        return pending instanceof Authentication authentication ? authentication : null;
    }

    private Map<String, String> scopeDescriptions(String scope, RegisteredClient client) {
        Map<String, String> applicationScopeDescriptions = applicationScopeDescriptions(client);
        Map<String, String> descriptions = new LinkedHashMap<>();
        Arrays.stream(scope.split(" "))
                .filter(value -> !value.isBlank())
                .forEach(value -> descriptions.put(value, switch (value) {
                    case "iam.read" -> "Read tenant IAM resources through protected Admin APIs.";
                    case "iam.write" -> "Create and update tenant IAM resources through protected Admin APIs.";
                    case "openid" -> "Sign you in.";
                    case "profile" -> "View your basic profile.";
                    case "email" -> "View your email address.";
                    case "groups" -> "View your group memberships.";
                    case "roles" -> "View your assigned roles.";
                    case "iam.profile" -> "View IAM-specific profile and authorization details.";
                    default -> applicationScopeDescriptions.getOrDefault(
                            value, "Application-specific delegated access requested by this client.");
                }));
        return descriptions;
    }

    private Map<String, String> applicationScopeDescriptions(RegisteredClient registeredClient) {
        ClientRepository repository = clientRepository.getIfAvailable();
        if (registeredClient == null || repository == null) {
            return Map.of();
        }
        try {
            return repository.findById(UUID.fromString(registeredClient.getId()))
                    .stream()
                    .flatMap(client -> client.getAllowedResourcePermissions().stream())
                    .collect(Collectors.toMap(
                            ResourcePermission::getName,
                            permission -> permission.getDisplayName() + ": " + permission.getDescription(),
                            (first, ignored) -> first,
                            LinkedHashMap::new));
        } catch (IllegalArgumentException ignored) {
            return Map.of();
        }
    }

    private String csrfInput(HttpServletRequest request) {
        CsrfToken csrf = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
        if (csrf == null) {
            return "";
        }
        return "<input type=\"hidden\" name=\"" + escape(csrf.getParameterName()) + "\" value=\"" + escape(csrf.getToken()) + "\">";
    }

    private String alert(String message) {
        return message == null || message.isBlank() ? "" : "<div class=\"alert\">" + escape(message) + "</div>";
    }

    private String notice(String message) {
        return message == null || message.isBlank() ? "" : "<div class=\"notice\">" + escape(message) + "</div>";
    }

    private ResponseEntity<String> redirect(String location) {
        return ResponseEntity.status(302).header("Location", location).build();
    }

    private ResponseEntity<String> html(String body) {
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_HTML)
                .body("""
                        <!doctype html>
                        <html lang="en">
                        <head>
                          <meta charset="utf-8">
                          <meta name="viewport" content="width=device-width, initial-scale=1">
                          <title>IdentityForge</title>
                          <style>
                            body{margin:0;background:#f5f7fb;color:#172033;font-family:Inter,system-ui,-apple-system,BlinkMacSystemFont,"Segoe UI",sans-serif}
                            main{width:min(460px,calc(100vw - 32px));margin:8vh auto;padding:24px;background:#fff;border:1px solid #d9e0ea;border-radius:8px;box-shadow:0 10px 30px rgba(15,23,42,.08)}
                            h1{margin:0 0 8px;font-size:24px;line-height:1.2}
                            .lede{margin:0 0 20px;color:#526173;font-size:14px;line-height:1.5}
                            form{display:grid;gap:14px}
                            label{display:grid;gap:6px;font-size:14px;font-weight:600;color:#334155}
                            input{min-height:42px;border:1px solid #d9e0ea;border-radius:6px;padding:0 12px;font-size:14px}
                            button{min-height:42px;border:0;border-radius:6px;background:#2563eb;color:white;font-weight:700;cursor:pointer}
                            .secondary button{background:#e2e8f0;color:#172033}
                            .actions{margin-top:14px}
                            .alert{margin:0 0 16px;border:1px solid #fecaca;background:#fef2f2;color:#991b1b;border-radius:6px;padding:10px;font-size:14px}
                            .notice{margin:0 0 16px;border:1px solid #bfdbfe;background:#eff6ff;color:#1e3a8a;border-radius:6px;padding:10px;font-size:14px}
                            .scopes{display:grid;gap:10px;margin:0 0 18px;padding:0;list-style:none}
                            .scopes li{display:grid;gap:3px;border:1px solid #d9e0ea;border-radius:6px;padding:10px}
                            .scopes span{font-size:13px;color:#526173}
                          </style>
                        </head>
                        <body><main>
                        %s
                        </main></body>
                        </html>
                        """.formatted(body));
    }

    private String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}

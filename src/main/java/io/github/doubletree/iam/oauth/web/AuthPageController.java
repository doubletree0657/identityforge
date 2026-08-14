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
                <div class="eyebrow">IAM Sign In</div>
                <h1>Sign in to IdentityForge</h1>
                <p class="lede">Use your tenant-qualified account to continue the secure authorization flow.</p>
                %s
                %s
                <form method="post" action="/login">
                  %s
                  <label>Realm / username<input name="username" autocomplete="username" placeholder="development/admin" aria-describedby="realm-help" required autofocus><span id="realm-help" class="hint">Enter <code>realm/username</code> so the account is resolved inside the correct tenant.</span></label>
                  <label>Password<input name="password" type="password" autocomplete="current-password" required><span class="hint">Your password stays on the authorization server and is never sent to the Admin Console.</span></label>
                  <button type="submit">Sign in</button>
                </form>
                <div class="security-note"><span aria-hidden="true">&#128274;</span><span>This browser flow uses a bounded server session, CSRF protection, and OAuth2 Authorization Code with PKCE.</span></div>
                """.formatted(alert(message), notice(logoutMessage), csrfInput(request)));
    }

    @GetMapping(value = "/login/mfa", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> mfa(HttpServletRequest request, @RequestParam(required = false) String error) {
        if (pendingAuthentication(request.getSession(false)) == null) {
            return redirect("/login");
        }
        return html("""
                <div class="eyebrow">Step 2 of 2</div>
                <h1>Verify it’s you</h1>
                <p class="lede">Your password was accepted. Complete the second factor to continue.</p>
                %s
                <form method="post" action="/login/mfa">
                  %s
                  <label>Authenticator or recovery code<input name="code" autocomplete="one-time-code" inputmode="numeric" placeholder="6-digit code or recovery code" aria-describedby="mfa-help" required autofocus><span id="mfa-help" class="hint">Recovery codes work once. Spaces and hyphens are accepted when present in your saved code.</span></label>
                  <button type="submit">Verify and continue</button>
                </form>
                <div class="security-note"><span aria-hidden="true">&#128737;</span><span>Attempts are throttled. IdentityForge never records the submitted code in audit details.</span></div>
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
                <div class="eyebrow">OAuth2 Consent</div>
                <h1>Allow %s?</h1>
                <p class="lede">Signed in as <strong>%s</strong>. This application is asking for the following delegated access.</p>
                <ul class="scopes">%s</ul>
                <p class="consent-note">Only approve if you recognize this application. Approval stores a consent grant; it does not reveal your password or MFA credentials.</p>
                <form method="post" action="/oauth2/authorize" class="actions">
                  %s
                  <input type="hidden" name="client_id" value="%s">
                  <input type="hidden" name="state" value="%s">
                  %s
                  <button type="submit">Allow access</button>
                </form>
                <form method="post" action="/oauth2/authorize" class="actions secondary">
                  %s
                  <input type="hidden" name="client_id" value="%s">
                  <input type="hidden" name="state" value="%s">
                  <button type="submit">Deny and return</button>
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

    @GetMapping(value = "/logout", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> logout(HttpServletRequest request, Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return redirect("/login");
        }
        return html("""
                <div class="eyebrow">Session security</div>
                <h1>Sign out of IdentityForge?</h1>
                <p class="lede">This ends the browser session and revokes grants issued to the Admin Console. Other application sessions may have their own lifecycle.</p>
                <form method="post" action="/logout">
                  %s
                  <button type="submit">Sign out securely</button>
                </form>
                """.formatted(csrfInput(request)));
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
                .header("Cache-Control", "no-store")
                .header("Pragma", "no-cache")
                .body("""
                        <!doctype html>
                        <html lang="en">
                        <head>
                          <meta charset="utf-8">
                          <meta name="viewport" content="width=device-width, initial-scale=1">
                          <title>IdentityForge</title>
                          <style>
                            *{box-sizing:border-box}
                            body{margin:0;min-height:100vh;background:linear-gradient(135deg,#edf3f2 0,#f8fafc 62vw);color:#172033;font-family:Inter,system-ui,-apple-system,BlinkMacSystemFont,"Segoe UI",sans-serif}
                            body:before{content:"IdentityForge";display:block;width:min(500px,calc(100vw - 32px));margin:5vh auto -3vh;color:#1f7a6d;font-size:15px;font-weight:750;letter-spacing:-.01em}
                            main{width:min(500px,calc(100vw - 32px));margin:5vh auto;padding:32px;background:#fff;border:1px solid #d7dde8;border-radius:16px;box-shadow:0 20px 55px rgba(15,23,42,.12)}
                            h1{margin:6px 0 10px;font-size:28px;line-height:1.2;letter-spacing:-.025em}
                            .eyebrow{color:#1f7a6d;font-size:11px;font-weight:750;letter-spacing:.14em;text-transform:uppercase}
                            .lede{margin:0 0 22px;color:#526173;font-size:14px;line-height:1.65}
                            form{display:grid;gap:14px}
                            label{display:grid;gap:6px;font-size:14px;font-weight:600;color:#334155}
                            input{min-height:44px;border:1px solid #cbd5e1;border-radius:8px;padding:0 12px;color:#172033;font-size:15px;outline:0;transition:.15s}
                            input:focus{border-color:#1f7a6d;box-shadow:0 0 0 3px rgba(31,122,109,.16)}
                            button{min-height:44px;border:0;border-radius:8px;background:#1f7a6d;color:white;font-weight:700;cursor:pointer;transition:.15s}
                            button:hover{background:#155e55}
                            .secondary button{border:1px solid #d7dde8;background:white;color:#334155}
                            .secondary button:hover{background:#f8fafc}
                            .actions{margin-top:14px}
                            .alert{margin:0 0 16px;border:1px solid #fecaca;background:#fff7f6;color:#991b1b;border-radius:8px;padding:12px;font-size:14px}
                            .notice{margin:0 0 16px;border:1px solid #a7f3d0;background:#ecfdf5;color:#166534;border-radius:8px;padding:12px;font-size:14px}
                            .hint{color:#64748b;font-size:12px;font-weight:400;line-height:1.45}
                            .hint code{font-size:11px}
                            .security-note{display:flex;gap:9px;margin-top:22px;padding-top:18px;border-top:1px solid #e2e8f0;color:#64748b;font-size:12px;line-height:1.5}
                            .scopes{display:grid;gap:10px;margin:0 0 18px;padding:0;list-style:none}
                            .scopes li{display:grid;gap:4px;border:1px solid #d7dde8;border-radius:9px;padding:12px;background:#f8fafc}
                            .scopes strong{font-family:ui-monospace,SFMono-Regular,Menlo,monospace;font-size:13px}
                            .scopes span{font-size:13px;color:#526173;line-height:1.45}
                            .consent-note{margin:0 0 18px;color:#64748b;font-size:12px;line-height:1.55}
                            @media(max-width:540px){body:before{margin-top:24px}main{margin:24px auto;padding:24px}h1{font-size:25px}}
                            @media(prefers-reduced-motion:reduce){*{transition:none!important}}
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

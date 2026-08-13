package io.github.doubletree.iam.oauth.infrastructure;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import io.github.doubletree.iam.audit.application.AuditApplicationService;
import io.github.doubletree.iam.applications.infrastructure.persistence.ClientRepository;
import io.github.doubletree.iam.directory.access.infrastructure.AdminApiAuthorizationManager;
import io.github.doubletree.iam.authentication.infrastructure.MfaAuthenticationSuccessHandler;
import io.github.doubletree.iam.authentication.infrastructure.PlatformUserDetails;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.security.Principal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.endpoint.OAuth2AccessTokenResponse;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.security.oauth2.core.http.converter.OAuth2AccessTokenResponseHttpMessageConverter;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import io.github.doubletree.iam.authentication.application.UserSecurityStateService;
import org.springframework.security.oauth2.server.authorization.InMemoryOAuth2AuthorizationConsentService;
import org.springframework.security.oauth2.server.authorization.JdbcOAuth2AuthorizationConsentService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationConsentService;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AccessTokenAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationCodeRequestAuthenticationException;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationCodeRequestAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2ClientAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2TokenRevocationAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.oauth2.server.resource.web.access.BearerTokenAccessDeniedHandler;
import org.springframework.security.oauth2.server.authorization.config.annotation.web.configuration.OAuth2AuthorizationServerConfiguration;
import org.springframework.security.oauth2.server.authorization.config.annotation.web.configurers.OAuth2AuthorizationServerConfigurer;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.web.DefaultRedirectStrategy;
import org.springframework.security.web.RedirectStrategy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.authentication.logout.CookieClearingLogoutHandler;
import org.springframework.security.web.authentication.logout.LogoutSuccessHandler;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.util.matcher.MediaTypeRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.util.StringUtils;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.util.UriUtils;

@Configuration
public class AuthorizationServerConfiguration {

    @Bean
    OidcIdentityClaims oidcIdentityClaims() {
        return new OidcIdentityClaims();
    }

    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    SecurityFilterChain authorizationServerSecurityFilterChain(
            HttpSecurity http,
            AuditApplicationService auditApplicationService,
            ObjectProvider<ClientRepository> clientRepository,
            OidcIdentityClaims oidcIdentityClaims) throws Exception {
        OAuth2AuthorizationServerConfigurer authorizationServerConfigurer =
                OAuth2AuthorizationServerConfigurer.authorizationServer();
        RequestMatcher authorizationServerEndpointsMatcher = authorizationServerConfigurer.getEndpointsMatcher();

        http
                .securityMatcher(authorizationServerEndpointsMatcher)
                .with(authorizationServerConfigurer, authorizationServer -> authorizationServer
                        .oidc(oidc -> oidc.userInfoEndpoint(userInfo -> userInfo
                                .userInfoMapper(context -> {
                                    Authentication principal = context.getAuthorization()
                                            .getAttribute(Principal.class.getName());
                                    if (principal == null
                                            || !(principal.getPrincipal() instanceof PlatformUserDetails userDetails)) {
                                        return new OidcUserInfo(Map.of());
                                    }
                                    return new OidcUserInfo(oidcIdentityClaims.userInfoClaims(
                                            userDetails, context.getAccessToken().getScopes()));
                                })))
                        .authorizationEndpoint(authorizationEndpoint -> authorizationEndpoint
                                .consentPage("/oauth2/consent")
                                .authorizationResponseHandler((request, response, authentication) -> {
                                    auditConsent(request, authentication, auditApplicationService, true);
                                    sendAuthorizationResponse(request, response, authentication);
                                })
                                .errorResponseHandler((request, response, exception) -> {
                                    auditConsent(request, null, auditApplicationService, false);
                                    sendErrorResponse(request, response, exception);
                                }))
                        .tokenEndpoint(tokenEndpoint -> tokenEndpoint
                                .accessTokenResponseHandler((request, response, authentication) -> {
                                    auditTokenRefresh(request, authentication, auditApplicationService, clientRepository);
                                    sendTokenResponse(response, authentication);
                                }))
                        .tokenRevocationEndpoint(tokenRevocationEndpoint -> tokenRevocationEndpoint
                                .revocationResponseHandler((request, response, authentication) -> {
                                    auditTokenRevocation(authentication, auditApplicationService, clientRepository);
                                    response.setStatus(HttpStatus.OK.value());
                                })))
                .authorizeHttpRequests(authorize -> authorize.anyRequest().authenticated())
                .exceptionHandling(exceptions -> exceptions
                        .defaultAuthenticationEntryPointFor(
                                new BearerTokenAuthenticationEntryPoint(),
                                PathPatternRequestMatcher.withDefaults().matcher("/userinfo"))
                        .defaultAuthenticationEntryPointFor(
                                new LoginUrlAuthenticationEntryPoint("/login"),
                                new MediaTypeRequestMatcher(MediaType.TEXT_HTML)))
                .cors(Customizer.withDefaults())
                .csrf(csrf -> csrf.ignoringRequestMatchers(authorizationServerEndpointsMatcher));

        return http.build();
    }

    @Bean
    @Order(Ordered.LOWEST_PRECEDENCE)
    SecurityFilterChain applicationSecurityFilterChain(
            HttpSecurity http,
            MfaAuthenticationSuccessHandler mfaAuthenticationSuccessHandler,
            AuditApplicationService auditApplicationService,
            @Value("${app.admin-console.frontend-base-url}") String adminConsoleFrontendBaseUrl)
            throws Exception {
        RequestMatcher apiEndpointsMatcher = new OrRequestMatcher(
                PathPatternRequestMatcher.withDefaults().matcher("/api/**"),
                PathPatternRequestMatcher.withDefaults().matcher("/scim/v2/**"),
                PathPatternRequestMatcher.withDefaults().matcher("/demo-resource-api/**"));
        RequestMatcher scimEndpointsMatcher =
                PathPatternRequestMatcher.withDefaults().matcher("/scim/v2/**");

        http
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/api/health").permitAll()
                        .requestMatchers("/login", "/login/mfa", "/logout").permitAll()
                        .requestMatchers("/oauth2/consent").authenticated()
                        .requestMatchers("/api/me").hasAuthority("SCOPE_iam.read")
                        .requestMatchers(HttpMethod.GET, "/api/oauth2/consents/me").hasAuthority("SCOPE_iam.read")
                        .requestMatchers(HttpMethod.DELETE, "/api/oauth2/consents/me/*").hasAuthority("SCOPE_iam.write")
                        .requestMatchers(HttpMethod.POST, "/api/**").access(new AdminApiAuthorizationManager("iam.write"))
                        .requestMatchers(HttpMethod.PUT, "/api/**").access(new AdminApiAuthorizationManager("iam.write"))
                        .requestMatchers(HttpMethod.PATCH, "/api/**").access(new AdminApiAuthorizationManager("iam.write"))
                        .requestMatchers(HttpMethod.DELETE, "/api/**").access(new AdminApiAuthorizationManager("iam.write"))
                        .requestMatchers("/api/**").access(new AdminApiAuthorizationManager("iam.read"))
                        .requestMatchers(HttpMethod.POST, "/scim/v2/**")
                        .access(new AdminApiAuthorizationManager("iam.write"))
                        .requestMatchers(HttpMethod.PUT, "/scim/v2/**")
                        .access(new AdminApiAuthorizationManager("iam.write"))
                        .requestMatchers(HttpMethod.PATCH, "/scim/v2/**")
                        .access(new AdminApiAuthorizationManager("iam.write"))
                        .requestMatchers(HttpMethod.DELETE, "/scim/v2/**")
                        .access(new AdminApiAuthorizationManager("iam.write"))
                        .requestMatchers("/scim/v2/**")
                        .access(new AdminApiAuthorizationManager("iam.read"))
                        .requestMatchers(HttpMethod.GET, "/demo-resource-api/payroll/employees")
                        .access(new AudienceScopeAuthorizationManager("payroll-api", "payroll.employee.read"))
                        .requestMatchers(HttpMethod.GET, "/demo-resource-api/payroll/salaries")
                        .access(new AudienceScopeAuthorizationManager("payroll-api", "payroll.salary.read"))
                        .requestMatchers(HttpMethod.POST, "/demo-resource-api/payroll/salaries")
                        .access(new AudienceScopeAuthorizationManager("payroll-api", "payroll.salary.write"))
                        .requestMatchers("/demo-resource-api/**").authenticated()
                        .anyRequest().permitAll())
                .formLogin(form -> form
                        .loginPage("/login")
                        .failureUrl("/login?error")
                        .successHandler(mfaAuthenticationSuccessHandler)
                        .permitAll())
                .logout(logout -> logout
                        .logoutRequestMatcher(PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.GET, "/logout"))
                        .addLogoutHandler(new SecurityContextLogoutHandler())
                        .addLogoutHandler(new CookieClearingLogoutHandler("JSESSIONID"))
                        .invalidateHttpSession(true)
                        .clearAuthentication(true)
                        .logoutSuccessHandler(logoutSuccessHandler(auditApplicationService, adminConsoleFrontendBaseUrl)))
                .exceptionHandling(exceptions -> exceptions
                        .defaultAuthenticationEntryPointFor(
                                (request, response, exception) -> {
                                    response.setStatus(HttpStatus.UNAUTHORIZED.value());
                                    response.setHeader("WWW-Authenticate", "Bearer");
                                    writeScimSecurityError(response, HttpStatus.UNAUTHORIZED, "Authentication is required");
                                },
                                scimEndpointsMatcher)
                        .defaultAccessDeniedHandlerFor(
                                (request, response, exception) -> {
                                    response.setStatus(HttpStatus.FORBIDDEN.value());
                                    writeScimSecurityError(response, HttpStatus.FORBIDDEN, "The request is not authorized");
                                },
                                scimEndpointsMatcher)
                        .defaultAuthenticationEntryPointFor(
                                new BearerTokenAuthenticationEntryPoint(),
                                apiEndpointsMatcher)
                        .defaultAccessDeniedHandlerFor(
                                new BearerTokenAccessDeniedHandler(),
                                apiEndpointsMatcher))
                .cors(Customizer.withDefaults())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()))
                .csrf(csrf -> csrf.ignoringRequestMatchers(apiEndpointsMatcher));

        return http.build();
    }

    private void writeScimSecurityError(
            HttpServletResponse response,
            HttpStatus status,
            String detail) throws IOException {
        response.setContentType("application/scim+json");
        response.getWriter().write("{\"schemas\":[\"urn:ietf:params:scim:api:messages:2.0:Error\"],"
                + "\"status\":\"" + status.value() + "\",\"detail\":\"" + detail + "\"}");
    }

    private void auditTokenRefresh(
            HttpServletRequest request,
            Authentication authentication,
            AuditApplicationService auditApplicationService,
            ObjectProvider<ClientRepository> clientRepository) {
        if (!"refresh_token".equals(request.getParameter(OAuth2ParameterNames.GRANT_TYPE))
                || !(authentication instanceof OAuth2AccessTokenAuthenticationToken tokenAuthentication)) {
            return;
        }
        auditByRegisteredClientId(
                tokenAuthentication.getRegisteredClient().getId(),
                "OAUTH2_TOKEN_REFRESHED",
                auditApplicationService,
                clientRepository);
    }

    private void auditTokenRevocation(
            Authentication authentication,
            AuditApplicationService auditApplicationService,
            ObjectProvider<ClientRepository> clientRepository) {
        if (!(authentication instanceof OAuth2TokenRevocationAuthenticationToken revocationAuthentication)
                || !(revocationAuthentication.getPrincipal() instanceof OAuth2ClientAuthenticationToken clientAuthentication)
                || clientAuthentication.getRegisteredClient() == null) {
            return;
        }
        auditByRegisteredClientId(
                clientAuthentication.getRegisteredClient().getId(),
                "OAUTH2_TOKEN_REVOKED",
                auditApplicationService,
                clientRepository);
    }

    private void auditByRegisteredClientId(
            String registeredClientId,
            String action,
            AuditApplicationService auditApplicationService,
            ObjectProvider<ClientRepository> clientRepository) {
        ClientRepository repository = clientRepository.getIfAvailable();
        if (repository == null) {
            return;
        }
        try {
            repository.findById(UUID.fromString(registeredClientId))
                    .ifPresent(client -> auditApplicationService.recordEvent(
                            client.getTenant().getId(), action, "CLIENT", client.getId()));
        } catch (IllegalArgumentException ignored) {
            // Registered client ids are internal UUIDs for persisted clients.
        }
    }

    private void sendTokenResponse(
            HttpServletResponse response,
            Authentication authentication) throws IOException {
        OAuth2AccessTokenAuthenticationToken accessTokenAuthentication =
                (OAuth2AccessTokenAuthenticationToken) authentication;
        var accessToken = accessTokenAuthentication.getAccessToken();
        OAuth2AccessTokenResponse.Builder builder = OAuth2AccessTokenResponse
                .withToken(accessToken.getTokenValue())
                .tokenType(accessToken.getTokenType())
                .scopes(accessToken.getScopes());
        if (accessToken.getIssuedAt() != null && accessToken.getExpiresAt() != null) {
            builder.expiresIn(Duration.between(accessToken.getIssuedAt(), accessToken.getExpiresAt()).getSeconds());
        }
        if (accessTokenAuthentication.getRefreshToken() != null) {
            builder.refreshToken(accessTokenAuthentication.getRefreshToken().getTokenValue());
        }
        builder.additionalParameters(accessTokenAuthentication.getAdditionalParameters());
        new OAuth2AccessTokenResponseHttpMessageConverter()
                .write(builder.build(), MediaType.APPLICATION_JSON, new ServletServerHttpResponse(response));
    }

    private LogoutSuccessHandler logoutSuccessHandler(
            AuditApplicationService auditApplicationService,
            String adminConsoleFrontendBaseUrl) {
        return (request, response, authentication) -> {
            if (authentication != null && authentication.getPrincipal() instanceof PlatformUserDetails userDetails) {
                auditApplicationService.recordEvent(
                        userDetails.tenantId(), "USER_LOGGED_OUT", "USER", userDetails.userId());
            }
            response.sendRedirect(frontendLogoutRedirectUri(adminConsoleFrontendBaseUrl));
        };
    }

    private String frontendLogoutRedirectUri(String adminConsoleFrontendBaseUrl) {
        return UriComponentsBuilder.fromUriString(adminConsoleFrontendBaseUrl)
                .replacePath("/login")
                .replaceQuery(null)
                .queryParam("loggedOut", "true")
                .build()
                .toUriString();
    }

    private void auditConsent(
            HttpServletRequest request,
            Authentication authentication,
            AuditApplicationService auditApplicationService,
            boolean approved) {
        if (!isConsentPost(request)) {
            return;
        }
        Authentication principal = authentication;
        if (principal instanceof OAuth2AuthorizationCodeRequestAuthenticationToken authorization) {
            Object authorizationPrincipal = authorization.getPrincipal();
            principal = authorizationPrincipal instanceof Authentication userAuthentication
                    ? userAuthentication
                    : null;
        }
        if (principal == null) {
            principal = org.springframework.security.core.context.SecurityContextHolder
                    .getContext()
                    .getAuthentication();
        }
        if (principal != null && principal.getPrincipal() instanceof PlatformUserDetails userDetails) {
            auditApplicationService.recordEvent(
                    userDetails.tenantId(),
                    approved ? "OAUTH2_CONSENT_APPROVED" : "OAUTH2_CONSENT_DENIED",
                    "USER",
                    userDetails.userId());
        }
    }

    private boolean isConsentPost(HttpServletRequest request) {
        return "POST".equals(request.getMethod())
                && request.getRequestURI().endsWith("/oauth2/authorize")
                && request.getParameter(OAuth2ParameterNames.CLIENT_ID) != null
                && request.getParameter(OAuth2ParameterNames.STATE) != null
                && request.getParameter(OAuth2ParameterNames.RESPONSE_TYPE) == null;
    }

    private void sendAuthorizationResponse(
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication) throws IOException {
        OAuth2AuthorizationCodeRequestAuthenticationToken authorization =
                (OAuth2AuthorizationCodeRequestAuthenticationToken) authentication;
        UriComponentsBuilder uriBuilder = UriComponentsBuilder
                .fromUriString(authorization.getRedirectUri())
                .queryParam(OAuth2ParameterNames.CODE, authorization.getAuthorizationCode().getTokenValue());
        if (StringUtils.hasText(authorization.getState())) {
            uriBuilder.queryParam(
                    OAuth2ParameterNames.STATE,
                    UriUtils.encode(authorization.getState(), StandardCharsets.UTF_8));
        }
        redirectStrategy().sendRedirect(request, response, uriBuilder.build(true).toUriString());
    }

    private void sendErrorResponse(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException exception) throws IOException {
        OAuth2AuthorizationCodeRequestAuthenticationException authorizationException =
                (OAuth2AuthorizationCodeRequestAuthenticationException) exception;
        OAuth2Error error = authorizationException.getError();
        OAuth2AuthorizationCodeRequestAuthenticationToken authorization =
                authorizationException.getAuthorizationCodeRequestAuthentication();

        if (authorization == null || !StringUtils.hasText(authorization.getRedirectUri())) {
            response.sendError(HttpStatus.BAD_REQUEST.value(), error.toString());
            return;
        }

        UriComponentsBuilder uriBuilder = UriComponentsBuilder
                .fromUriString(authorization.getRedirectUri())
                .queryParam(OAuth2ParameterNames.ERROR, error.getErrorCode());
        if (StringUtils.hasText(error.getDescription())) {
            uriBuilder.queryParam(
                    OAuth2ParameterNames.ERROR_DESCRIPTION,
                    UriUtils.encode(error.getDescription(), StandardCharsets.UTF_8));
        }
        if (StringUtils.hasText(error.getUri())) {
            uriBuilder.queryParam(
                    OAuth2ParameterNames.ERROR_URI,
                    UriUtils.encode(error.getUri(), StandardCharsets.UTF_8));
        }
        if (StringUtils.hasText(authorization.getState())) {
            uriBuilder.queryParam(
                    OAuth2ParameterNames.STATE,
                    UriUtils.encode(authorization.getState(), StandardCharsets.UTF_8));
        }
        redirectStrategy().sendRedirect(request, response, uriBuilder.build(true).toUriString());
    }

    private RedirectStrategy redirectStrategy() {
        return new DefaultRedirectStrategy();
    }

    @Bean
    OAuth2AuthorizationConsentService authorizationConsentService(
            ObjectProvider<JdbcOperations> jdbcOperations,
            RegisteredClientRepository registeredClientRepository) {
        JdbcOperations operations = jdbcOperations.getIfAvailable();
        return operations == null
                ? new InMemoryOAuth2AuthorizationConsentService()
                : new JdbcOAuth2AuthorizationConsentService(operations, registeredClientRepository);
    }

    @Bean
    JWKSource<SecurityContext> jwkSource(SigningKeyProvider signingKeyProvider) {
        return new ImmutableJWKSet<>(new JWKSet(signingKeyProvider.currentKey()));
    }

    @Bean
    JwtDecoder jwtDecoder(
            JWKSource<SecurityContext> jwkSource,
            AuthorizationServerSettings settings,
            UserSecurityStateService securityStateService) {
        JwtDecoder decoder = OAuth2AuthorizationServerConfiguration.jwtDecoder(jwkSource);
        if (decoder instanceof NimbusJwtDecoder nimbusJwtDecoder) {
            nimbusJwtDecoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                    JwtValidators.createDefaultWithIssuer(settings.getIssuer()),
                    new UserSecurityStateTokenValidator(securityStateService)));
        }
        return decoder;
    }

    @Bean
    AuthorizationServerSettings authorizationServerSettings(
            @Value("${iam.oauth.issuer}") String issuer) {
        return AuthorizationServerSettings.builder()
                .issuer(issuer)
                .build();
    }

    @Bean
    OAuth2TokenCustomizer<JwtEncodingContext> jwtTokenCustomizer(
            OidcIdentityClaims oidcIdentityClaims,
            ObjectProvider<ClientRepository> clientRepository) {
        return context -> {
            var client = resolveClient(context.getRegisteredClient().getId(), clientRepository);
            String audience = client != null && client.getResourceServer() != null
                    ? client.getResourceServer().getIdentifier()
                    : "identityforge-admin-api";
            context.getClaims().audience(java.util.List.of(audience));
            if (!(context.getPrincipal().getPrincipal() instanceof PlatformUserDetails userDetails)) {
                return;
            }
            if ("id_token".equals(context.getTokenType().getValue())) {
                oidcIdentityClaims.idTokenClaims(userDetails, context.getAuthorizedScopes())
                        .forEach(context.getClaims()::claim);
                return;
            }
            if (!OAuth2TokenType.ACCESS_TOKEN.equals(context.getTokenType())) {
                return;
            }
            context.getClaims()
                    .subject(userDetails.userId().toString())
                    .claim("user_id", userDetails.userId().toString())
                    .claim("tenant_id", userDetails.tenantId().toString())
                    .claim("preferred_username", userDetails.getUsername())
                    .claim("display_name", userDetails.displayName())
                    .claim("security_version", userDetails.securityVersion());
            if ("identityforge-admin-api".equals(audience)) {
                context.getClaims()
                        .claim("platform_operator", userDetails.platformOperator())
                        .claim("effective_roles", userDetails.effectiveRoles())
                        .claim("effective_permissions", userDetails.effectivePermissions());
            }
        };
    }

    private io.github.doubletree.iam.applications.domain.Client resolveClient(
            String registeredClientId,
            ObjectProvider<ClientRepository> clientRepository) {
        ClientRepository repository = clientRepository.getIfAvailable();
        if (repository == null) {
            return null;
        }
        try {
            return repository.findById(UUID.fromString(registeredClientId)).orElse(null);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

}

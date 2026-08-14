package io.github.doubletree.iam.oauth.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import io.github.doubletree.iam.audit.application.AuditApplicationService;
import io.github.doubletree.iam.authentication.application.MfaApplicationService;
import io.github.doubletree.iam.authentication.application.UserSecurityStateService;
import io.github.doubletree.iam.authentication.infrastructure.MfaAuthenticationSuccessHandler;
import io.github.doubletree.iam.authentication.infrastructure.PasswordEncodingConfiguration;
import io.github.doubletree.iam.authentication.infrastructure.PlatformUserDetails;
import io.github.doubletree.iam.directory.domain.AccountStatus;
import io.github.doubletree.iam.oauth.application.OAuth2AuthorizationLifecycleService;
import io.github.doubletree.iam.oauth.web.AuthPageController;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.HttpCookie;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.server.authorization.client.InMemoryRegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;

@SpringBootTest(
        classes = BrowserLoginHttpIntegrationTests.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "iam.oauth.issuer=http://localhost",
                "iam.http.allowed-origins=http://localhost:5173",
                "app.admin-console.frontend-base-url=http://localhost:5173",
                "server.servlet.session.cookie.name=IDENTITYFORGE_SESSION",
                "server.servlet.session.cookie.secure=false",
                "server.servlet.session.cookie.same-site=lax",
                "spring.autoconfigure.exclude="
                        + "org.springframework.modulith.events.jpa.JpaEventPublicationAutoConfiguration,"
                        + "org.springframework.modulith.events.jpa.archiving.ArchivingAutoConfiguration"
        })
class BrowserLoginHttpIntegrationTests {

    private static final Pattern CSRF_INPUT = Pattern.compile("name=\"_csrf\" value=\"([^\"]+)\"");

    @LocalServerPort
    private int port;

    @Test
    void browserCookieAndRenderedCsrfTokenCompleteTheFirstLoginPost() throws Exception {
        CookieManager cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        HttpClient browser = HttpClient.newBuilder()
                .cookieHandler(cookies)
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        String backend = "http://localhost:" + port;
        HttpCookie unrelatedLocalSpringSession = new HttpCookie("JSESSIONID", "another-local-application");
        unrelatedLocalSpringSession.setPath("/");
        cookies.getCookieStore().add(URI.create(backend), unrelatedLocalSpringSession);
        URI authorize = URI.create(backend + "/oauth2/authorize"
                + "?response_type=code"
                + "&client_id=identityforge-console"
                + "&redirect_uri=http%3A%2F%2Flocalhost%3A5173%2Foauth2%2Fcallback"
                + "&scope=openid%20profile%20iam.read%20iam.write"
                + "&state=real-http-first-attempt"
                + "&code_challenge=0123456789012345678901234567890123456789012"
                + "&code_challenge_method=S256");

        HttpResponse<String> authorizationStart = browser.send(
                HttpRequest.newBuilder(authorize).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(authorizationStart.statusCode()).isEqualTo(302);

        HttpResponse<String> loginPage = browser.send(
                HttpRequest.newBuilder(resolve(backend, authorizationStart.headers().firstValue("Location").orElseThrow()))
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(loginPage.statusCode()).isEqualTo(200);
        String csrf = renderedCsrfToken(loginPage.body());
        assertThat(cookies.getCookieStore().getCookies().stream()
                .filter(cookie -> "IDENTITYFORGE_SESSION".equals(cookie.getName())))
                .singleElement()
                .satisfies(cookie -> {
                    assertThat(cookie.getName()).isEqualTo("IDENTITYFORGE_SESSION");
                    assertThat(cookie.getSecure()).isFalse();
                });

        String form = formField("_csrf", csrf)
                + "&" + formField("username", "development/admin")
                + "&" + formField("password", "admin123456");
        HttpResponse<String> login = browser.send(
                HttpRequest.newBuilder(URI.create(backend + "/login"))
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .POST(HttpRequest.BodyPublishers.ofString(form))
                        .build(),
                HttpResponse.BodyHandlers.ofString());

        assertThat(login.statusCode()).isEqualTo(302);
        assertThat(login.headers().firstValue("Location").orElseThrow())
                .startsWith(backend + "/oauth2/authorize")
                .doesNotContain("reason=request");
    }

    private URI resolve(String backend, String location) {
        URI uri = URI.create(location);
        return uri.isAbsolute() ? uri : URI.create(backend + location);
    }

    private String renderedCsrfToken(String html) {
        var matcher = CSRF_INPUT.matcher(html);
        assertThat(matcher.find()).as("rendered login CSRF input").isTrue();
        return matcher.group(1);
    }

    private String formField(String name, String value) {
        return URLEncoder.encode(name, StandardCharsets.UTF_8)
                + "=" + URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration(exclude = {
            DataSourceAutoConfiguration.class,
            HibernateJpaAutoConfiguration.class,
            FlywayAutoConfiguration.class
    })
    @Import({
            AuthorizationServerConfiguration.class,
            AuthPageController.class,
            MfaAuthenticationSuccessHandler.class,
            PasswordEncodingConfiguration.class
    })
    static class TestApplication {

        @Bean
        RegisteredClientRepository registeredClientRepository() {
            RegisteredClient client = RegisteredClient.withId(UUID.randomUUID().toString())
                    .clientId("identityforge-console")
                    .clientName("IdentityForge Console")
                    .clientAuthenticationMethod(ClientAuthenticationMethod.NONE)
                    .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                    .redirectUri("http://localhost:5173/oauth2/callback")
                    .scope("openid")
                    .scope("profile")
                    .scope("iam.read")
                    .scope("iam.write")
                    .clientSettings(ClientSettings.builder()
                            .requireProofKey(true)
                            .requireAuthorizationConsent(false)
                            .build())
                    .build();
            return new InMemoryRegisteredClientRepository(client);
        }

        @Bean
        UserDetailsService userDetailsService() {
            return ignored -> new PlatformUserDetails(
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    "admin",
                    "Development Super Admin",
                    "{noop}admin123456",
                    AccountStatus.ACTIVE,
                    Set.of("platform-admin"),
                    Set.of("iam.admin"));
        }

        @Bean
        MfaApplicationService mfaApplicationService() {
            return mock(MfaApplicationService.class);
        }

        @Bean
        AuditApplicationService auditApplicationService() {
            return mock(AuditApplicationService.class);
        }

        @Bean
        UserSecurityStateService userSecurityStateService() {
            UserSecurityStateService service = mock(UserSecurityStateService.class);
            when(service.isTokenStateCurrent(any(), anyInt())).thenReturn(true);
            return service;
        }

        @Bean
        OAuth2AuthorizationLifecycleService authorizationLifecycleService() {
            return mock(OAuth2AuthorizationLifecycleService.class);
        }

        @Bean
        AccessTokenAuthorizationState accessTokenAuthorizationState() {
            return token -> true;
        }

        @Bean
        SigningKeyProvider signingKeyProvider() throws Exception {
            var key = new RSAKeyGenerator(2048).keyID(UUID.randomUUID().toString()).generate();
            return () -> key;
        }
    }
}

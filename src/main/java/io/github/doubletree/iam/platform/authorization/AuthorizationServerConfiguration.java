package io.github.doubletree.iam.platform.authorization;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.UUID;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.oauth2.server.resource.web.access.BearerTokenAccessDeniedHandler;
import org.springframework.security.oauth2.server.authorization.config.annotation.web.configuration.OAuth2AuthorizationServerConfiguration;
import org.springframework.security.oauth2.server.authorization.config.annotation.web.configurers.OAuth2AuthorizationServerConfigurer;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.MediaTypeRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

@Configuration
public class AuthorizationServerConfiguration {

    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    SecurityFilterChain authorizationServerSecurityFilterChain(HttpSecurity http) throws Exception {
        OAuth2AuthorizationServerConfigurer authorizationServerConfigurer =
                OAuth2AuthorizationServerConfigurer.authorizationServer();
        RequestMatcher authorizationServerEndpointsMatcher = authorizationServerConfigurer.getEndpointsMatcher();

        http
                .securityMatcher(authorizationServerEndpointsMatcher)
                .with(authorizationServerConfigurer, Customizer.withDefaults())
                .authorizeHttpRequests(authorize -> authorize.anyRequest().authenticated())
                .exceptionHandling(exceptions -> exceptions.defaultAuthenticationEntryPointFor(
                        new LoginUrlAuthenticationEntryPoint("/login"),
                        new MediaTypeRequestMatcher(MediaType.TEXT_HTML)))
                .csrf(csrf -> csrf.ignoringRequestMatchers(authorizationServerEndpointsMatcher));

        return http.build();
    }

    @Bean    
    @Order(Ordered.LOWEST_PRECEDENCE)
    SecurityFilterChain applicationSecurityFilterChain(HttpSecurity http) throws Exception {
        RequestMatcher apiEndpointsMatcher = new OrRequestMatcher(
                AntPathRequestMatcher.antMatcher("/api/**"),
                AntPathRequestMatcher.antMatcher("/scim/v2/**"));

        http
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/api/health").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/**").hasAuthority("SCOPE_iam.write")
                        .requestMatchers(HttpMethod.PUT, "/api/**").hasAuthority("SCOPE_iam.write")
                        .requestMatchers(HttpMethod.PATCH, "/api/**").hasAuthority("SCOPE_iam.write")
                        .requestMatchers(HttpMethod.DELETE, "/api/**").hasAuthority("SCOPE_iam.write")
                        .requestMatchers("/api/**").hasAuthority("SCOPE_iam.read")
                        .requestMatchers(HttpMethod.POST, "/scim/v2/**").hasAuthority("SCOPE_iam.write")
                        .requestMatchers("/scim/v2/**").hasAuthority("SCOPE_iam.read")
                        .anyRequest().permitAll())
                .formLogin(Customizer.withDefaults())
                .logout(Customizer.withDefaults())
                .exceptionHandling(exceptions -> exceptions
                        .defaultAuthenticationEntryPointFor(
                                new BearerTokenAuthenticationEntryPoint(),
                                apiEndpointsMatcher)
                        .defaultAccessDeniedHandlerFor(
                                new BearerTokenAccessDeniedHandler(),
                                apiEndpointsMatcher))
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()))
                .csrf(csrf -> csrf.ignoringRequestMatchers(apiEndpointsMatcher));

        return http.build();
    }

    @Bean
    JWKSource<SecurityContext> jwkSource() {
        RSAKey localDevelopmentRsaKey = generateLocalDevelopmentRsaKey();
        return new ImmutableJWKSet<>(new JWKSet(localDevelopmentRsaKey));
    }

    @Bean
    JwtDecoder jwtDecoder(JWKSource<SecurityContext> jwkSource) {
        return OAuth2AuthorizationServerConfiguration.jwtDecoder(jwkSource);
    }

    @Bean
    AuthorizationServerSettings authorizationServerSettings() {
        return AuthorizationServerSettings.builder().build();
    }

    private RSAKey generateLocalDevelopmentRsaKey() {
        KeyPair keyPair = generateRsaKeyPair();
        RSAPublicKey publicKey = (RSAPublicKey) keyPair.getPublic();
        RSAPrivateKey privateKey = (RSAPrivateKey) keyPair.getPrivate();

        return new RSAKey.Builder(publicKey)
                .privateKey(privateKey)
                .keyID("local-development-only-" + UUID.randomUUID())
                .build();
    }

    private KeyPair generateRsaKeyPair() {
        try {
            KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
            keyPairGenerator.initialize(2048);
            return keyPairGenerator.generateKeyPair();
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to generate local development RSA key pair", exception);
        }
    }
}

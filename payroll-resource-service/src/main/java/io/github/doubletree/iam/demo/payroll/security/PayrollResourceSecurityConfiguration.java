package io.github.doubletree.iam.demo.payroll.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.util.StringUtils;

@Configuration
public class PayrollResourceSecurityConfiguration {

    public static final String PAYROLL_AUDIENCE = "payroll-api";
    public static final String EMPLOYEE_READ = "payroll.employee.read";
    public static final String SALARY_READ = "payroll.salary.read";
    public static final String SALARY_WRITE = "payroll.salary.write";

    @Bean
    SecurityFilterChain payrollSecurityFilterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/api/health").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/payroll/employees")
                        .hasAuthority("SCOPE_" + EMPLOYEE_READ)
                        .requestMatchers(HttpMethod.GET, "/api/payroll/salaries")
                        .hasAuthority("SCOPE_" + SALARY_READ)
                        .requestMatchers(HttpMethod.POST, "/api/payroll/salaries")
                        .hasAuthority("SCOPE_" + SALARY_WRITE)
                        .anyRequest().denyAll())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt
                        .jwtAuthenticationConverter(jwtAuthenticationConverter())))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .requestCache(cache -> cache.disable())
                .csrf(csrf -> csrf.disable());
        return http.build();
    }

    @Bean
    @Profile("!test")
    JwtDecoder payrollJwtDecoder(
            @Value("${identityforge.oauth.issuer}") String issuer,
            @Value("${identityforge.oauth.jwk-set-uri:}") String jwkSetUri,
            @Value("${payroll.oauth.audience:" + PAYROLL_AUDIENCE + "}") String audience) {
        NimbusJwtDecoder decoder = StringUtils.hasText(jwkSetUri)
                ? NimbusJwtDecoder.withJwkSetUri(jwkSetUri).build()
                : NimbusJwtDecoder.withIssuerLocation(issuer).build();
        decoder.setJwtValidator(tokenValidators(issuer, audience));
        return decoder;
    }

    public static OAuth2TokenValidator<Jwt> tokenValidators(String issuer, String audience) {
        return new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(issuer),
                audienceValidator(audience));
    }

    private static OAuth2TokenValidator<Jwt> audienceValidator(String audience) {
        OAuth2Error error = new OAuth2Error(
                "invalid_token",
                "The access token is not intended for the Payroll API",
                null);
        return token -> token.getAudience().contains(audience)
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(error);
    }

    private Converter<Jwt, ? extends AbstractAuthenticationToken> jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setPrincipalClaimName("sub");
        return converter;
    }
}

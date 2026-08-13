package io.github.doubletree.iam.demo.payroll;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import io.github.doubletree.iam.demo.payroll.security.PayrollResourceSecurityConfiguration;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(PayrollResourceServiceIntegrationTests.TestJwtConfiguration.class)
class PayrollResourceServiceIntegrationTests {

    private static final String ISSUER = "https://identityforge.test";
    private static final String AUDIENCE = "payroll-api";
    private static final String KEY_ID = "identityforge-test-key";
    private static final RSAKey SIGNING_KEY = signingKey();
    private static final RSAKey UNTRUSTED_SIGNING_KEY = signingKey("untrusted-test-key");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void healthIsPublicButPayrollRequiresABearerToken() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.service").value("payroll-resource-service"));

        mockMvc.perform(get("/api/payroll/employees"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, startsWith("Bearer")));
    }

    @Test
    void employeeScopeAllowsEmployeesWithoutDisclosingSalaryData() throws Exception {
        MvcResult response = performGet(
                "/api/payroll/employees",
                token(ISSUER, AUDIENCE, List.of("payroll.employee.read"), Instant.now().plusSeconds(300)));

        assertThat(response.getResponse().getStatus()).isEqualTo(HttpStatus.OK.value());
        JsonNode body = objectMapper.readTree(response.getResponse().getContentAsString());
        assertThat(body.get(0).get("employeeId").asText()).isEqualTo("E-1001");
        assertThat(body.get(0).has("salaryAmount")).isFalse();
    }

    @Test
    void endpointSpecificScopesProduceAllowedAndDeniedPayrollAccess() throws Exception {
        String employeeToken = token(
                ISSUER, AUDIENCE, List.of("payroll.employee.read"), Instant.now().plusSeconds(300));
        String salaryReadToken = token(
                ISSUER, AUDIENCE, List.of("payroll.salary.read"), Instant.now().plusSeconds(300));
        String salaryWriteToken = token(
                ISSUER, AUDIENCE, List.of("payroll.salary.write"), Instant.now().plusSeconds(300));

        assertThat(performGet("/api/payroll/salaries", employeeToken).getResponse().getStatus())
                .isEqualTo(HttpStatus.FORBIDDEN.value());

        MvcResult salaries = performGet("/api/payroll/salaries", salaryReadToken);
        assertThat(salaries.getResponse().getStatus()).isEqualTo(HttpStatus.OK.value());
        assertThat(objectMapper.readTree(salaries.getResponse().getContentAsString())
                        .get(0).get("salaryAmount").decimalValue())
                .isEqualByComparingTo("132000.00");

        assertThat(performPost("/api/payroll/salaries", salaryReadToken).getResponse().getStatus())
                .isEqualTo(HttpStatus.FORBIDDEN.value());
        MvcResult write = performPost("/api/payroll/salaries", salaryWriteToken);
        assertThat(write.getResponse().getStatus()).isEqualTo(HttpStatus.ACCEPTED.value());
        assertThat(objectMapper.readTree(write.getResponse().getContentAsString())
                        .get("status").asText())
                .isEqualTo("accepted");
    }

    @Test
    void invalidIdentityClaimsOrSignatureAreRejectedDuringAuthentication() throws Exception {
        String wrongAudience = token(
                ISSUER, "identityforge-admin-api", List.of("payroll.employee.read"), Instant.now().plusSeconds(300));
        String wrongIssuer = token(
                "https://other-issuer.test", AUDIENCE, List.of("payroll.employee.read"), Instant.now().plusSeconds(300));
        String expired = token(
                ISSUER, AUDIENCE, List.of("payroll.employee.read"), Instant.now().minusSeconds(300));
        String untrustedSignature = token(
                ISSUER,
                AUDIENCE,
                List.of("payroll.employee.read"),
                Instant.now().plusSeconds(300),
                UNTRUSTED_SIGNING_KEY);

        assertThat(performGet("/api/payroll/employees", wrongAudience).getResponse().getStatus())
                .isEqualTo(HttpStatus.UNAUTHORIZED.value());
        assertThat(performGet("/api/payroll/employees", wrongIssuer).getResponse().getStatus())
                .isEqualTo(HttpStatus.UNAUTHORIZED.value());
        assertThat(performGet("/api/payroll/employees", expired).getResponse().getStatus())
                .isEqualTo(HttpStatus.UNAUTHORIZED.value());
        assertThat(performGet("/api/payroll/employees", untrustedSignature).getResponse().getStatus())
                .isEqualTo(HttpStatus.UNAUTHORIZED.value());
    }

    private MvcResult performGet(String path, String accessToken) throws Exception {
        return mockMvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andReturn();
    }

    private MvcResult performPost(String path, String accessToken) throws Exception {
        return mockMvc.perform(post(path).header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andReturn();
    }

    private String token(String issuer, String audience, List<String> scopes, Instant expiresAt) throws Exception {
        return token(issuer, audience, scopes, expiresAt, SIGNING_KEY);
    }

    private String token(
            String issuer,
            String audience,
            List<String> scopes,
            Instant expiresAt,
            RSAKey signingKey) throws Exception {
        Instant now = Instant.now();
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer(issuer)
                .subject("00000000-0000-0000-0000-000000000002")
                .audience(audience)
                .issueTime(Date.from(now.minusSeconds(1)))
                .expirationTime(Date.from(expiresAt))
                .jwtID(java.util.UUID.randomUUID().toString())
                .claim("scope", scopes)
                .claim("tenant_id", "00000000-0000-0000-0000-000000000001")
                .build();
        SignedJWT jwt = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(signingKey.getKeyID()).build(),
                claims);
        jwt.sign(new RSASSASigner(signingKey.toPrivateKey()));
        return jwt.serialize();
    }

    private static RSAKey signingKey() {
        return signingKey(KEY_ID);
    }

    private static RSAKey signingKey(String keyId) {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            var pair = generator.generateKeyPair();
            return new RSAKey.Builder((RSAPublicKey) pair.getPublic())
                    .privateKey((RSAPrivateKey) pair.getPrivate())
                    .keyID(keyId)
                    .algorithm(JWSAlgorithm.RS256)
                    .build();
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to create integration-test signing key", exception);
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    @Profile("test")
    static class TestJwtConfiguration {

        @Bean
        JwtDecoder testJwtDecoder() throws Exception {
            NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(SIGNING_KEY.toRSAPublicKey()).build();
            decoder.setJwtValidator(PayrollResourceSecurityConfiguration.tokenValidators(ISSUER, AUDIENCE));
            return decoder;
        }
    }
}

package io.github.doubletree.iam.authentication.infrastructure.crypto;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SecretEncryptionServiceTests {

    private static final String TEST_KEY = "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=";

    private final SecretEncryptionService service = new SecretEncryptionService(TEST_KEY);

    @Test
    void recoveryCodeDigestIsDeterministicOpaqueAndDomainSpecific() {
        String recoveryCode = "ABCD-EFGH-JKLM-NPQR";

        String digest = service.recoveryCodeDigest(recoveryCode.replace("-", ""));

        assertThat(digest)
                .hasSize(43)
                .doesNotContain(recoveryCode)
                .isEqualTo(service.recoveryCodeDigest("ABCDEFGHJKLMNPQR"))
                .isNotEqualTo(service.recoveryCodeDigest("ABCDEFGHJKLMNPQ2"));
    }

    @Test
    void encryptionUsesFreshAuthenticatedCiphertext() {
        String first = service.encrypt("TOTPSECRET");
        String second = service.encrypt("TOTPSECRET");

        assertThat(first).startsWith("v1:").isNotEqualTo(second);
        assertThat(service.decrypt(first)).isEqualTo("TOTPSECRET");
        assertThat(service.decrypt(second)).isEqualTo("TOTPSECRET");
    }
}

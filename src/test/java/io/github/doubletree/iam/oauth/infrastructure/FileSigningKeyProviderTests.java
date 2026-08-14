package io.github.doubletree.iam.oauth.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileSigningKeyProviderTests {

    @Test
    void productionModeRefusesToGenerateAMissingSigningKey() {
        Path keyFile = tempDirectory.resolve("missing-signing-key.properties");

        assertThatThrownBy(() -> new FileSigningKeyProvider(keyFile.toString(), false))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("does not exist");
    }

    @TempDir
    Path tempDirectory;

    @Test
    void reloadsTheSameSigningKeyFromDurableStorage() throws Exception {
        Path keyFile = tempDirectory.resolve("oauth-signing-key.properties");

        var first = new FileSigningKeyProvider(keyFile.toString()).currentKey();
        var second = new FileSigningKeyProvider(keyFile.toString()).currentKey();

        assertThat(Files.exists(keyFile)).isTrue();
        assertThat(second.getKeyID()).isEqualTo(first.getKeyID());
        assertThat(second.toRSAPublicKey()).isEqualTo(first.toRSAPublicKey());
        assertThat(second.toRSAPrivateKey()).isEqualTo(first.toRSAPrivateKey());
    }
}

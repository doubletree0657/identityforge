package io.github.doubletree.iam.directory.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class UsernameNormalizerTests {

    @Test
    void canonicalizesCaseWhitespaceAndCompatibilityCharacters() {
        assertThat(UsernameNormalizer.normalize("  ALICE  ")).isEqualTo("alice");
        assertThat(UsernameNormalizer.normalize("Ａｌｉｃｅ")).isEqualTo("alice");
    }

    @Test
    void rejectsMissingUsernames() {
        assertThatThrownBy(() -> UsernameNormalizer.normalize("   "))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> UsernameNormalizer.normalize(null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}

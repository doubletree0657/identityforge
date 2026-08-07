package io.github.doubletree.iam.authentication.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class RealmLoginIdentifierTests {

    @Test
    void parsesExplicitRealmAndCanonicalUsername() {
        RealmLoginIdentifier identifier = RealmLoginIdentifier.parse("Engineering/ ＡＬＩＣＥ ");

        assertThat(identifier.realm()).isEqualTo("engineering");
        assertThat(identifier.normalizedUsername()).isEqualTo("alice");
    }

    @Test
    void rejectsAmbiguousOrInvalidLoginIdentifiers() {
        assertThatThrownBy(() -> RealmLoginIdentifier.parse("alice"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> RealmLoginIdentifier.parse("bad realm/alice"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> RealmLoginIdentifier.parse("realm/team/alice"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}

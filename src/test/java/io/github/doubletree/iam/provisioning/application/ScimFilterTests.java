package io.github.doubletree.iam.provisioning.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.doubletree.iam.provisioning.api.ScimProtocolException;
import org.junit.jupiter.api.Test;

class ScimFilterTests {

    @Test
    void parsesSupportedUserAndGroupEqualityFiltersCaseInsensitively() {
        ScimFilter userName = ScimFilter.parse("UserName EQ \"Alice\"", ScimFilter.Resource.USER);
        ScimFilter active = ScimFilter.parse("active eq false", ScimFilter.Resource.USER);
        ScimFilter member = ScimFilter.parse(
                "members.value eq \"00000000-0000-0000-0000-000000000001\"",
                ScimFilter.Resource.GROUP);

        assertThat(userName.attribute()).isEqualTo("username");
        assertThat(userName.stringValue()).isEqualTo("Alice");
        assertThat(active.booleanValue()).isFalse();
        assertThat(member.attribute()).isEqualTo("members.value");
    }

    @Test
    void rejectsUnsupportedOperatorsAttributesAndUnquotedStrings() {
        assertInvalid("userName co \"ali\"", ScimFilter.Resource.USER);
        assertInvalid("title eq \"Engineer\"", ScimFilter.Resource.USER);
        assertInvalid("userName eq alice", ScimFilter.Resource.USER);
        assertInvalid("displayName eq \"A\" and active eq true", ScimFilter.Resource.USER);
        assertInvalid("active eq yes", ScimFilter.Resource.USER);
    }

    private void assertInvalid(String expression, ScimFilter.Resource resource) {
        assertThatThrownBy(() -> ScimFilter.parse(expression, resource))
                .isInstanceOf(ScimProtocolException.class)
                .extracting(exception -> ((ScimProtocolException) exception).scimType())
                .isEqualTo("invalidFilter");
    }
}

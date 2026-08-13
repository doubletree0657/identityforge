package io.github.doubletree.iam.provisioning.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.doubletree.iam.provisioning.api.ScimProtocolException;
import org.junit.jupiter.api.Test;

class ScimPageRequestTests {

    @Test
    void convertsOneBasedStartIndexToAnArbitraryDatabaseOffset() {
        ScimPageRequest request = ScimPageRequest.of(2, 25);

        assertThat(request.getOffset()).isEqualTo(1);
        assertThat(request.getPageSize()).isEqualTo(25);
        assertThat(request.getSort().getOrderFor("createdAt")).isNotNull();
    }

    @Test
    void capsCountAndAllowsCountZeroForTotalOnlyQueries() {
        assertThat(ScimPageRequest.of(1, 1_000).getPageSize()).isEqualTo(100);
        assertThat(ScimPageRequest.of(1, 0).getPageSize()).isEqualTo(1);
    }

    @Test
    void rejectsInvalidPaginationValues() {
        assertThatThrownBy(() -> ScimPageRequest.of(0, 10))
                .isInstanceOf(ScimProtocolException.class);
        assertThatThrownBy(() -> ScimPageRequest.of(1, -1))
                .isInstanceOf(ScimProtocolException.class);
    }
}

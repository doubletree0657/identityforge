package io.github.doubletree.iam.authentication.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;

class MfaAttemptGuardTests {

    @Test
    void blocksAfterFiveFailuresAndResetClearsTheWindow() {
        UUID userId = UUID.randomUUID();
        Clock clock = Clock.fixed(Instant.parse("2026-08-07T00:00:00Z"), ZoneOffset.UTC);
        StaticListableBeanFactory beans = new StaticListableBeanFactory(Map.of("clock", clock));
        MfaAttemptGuard guard = new MfaAttemptGuard(beans.getBeanProvider(Clock.class));

        for (int attempt = 0; attempt < 5; attempt++) {
            assertThat(guard.isAllowed(userId)).isTrue();
            guard.recordFailure(userId);
        }

        assertThat(guard.isAllowed(userId)).isFalse();
        guard.reset(userId);
        assertThat(guard.isAllowed(userId)).isTrue();
    }
}

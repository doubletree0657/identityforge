package io.github.doubletree.iam.authentication.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

@Component
public class MfaAttemptGuard {

    private static final int MAX_FAILURES = 5;
    private static final Duration WINDOW = Duration.ofMinutes(5);

    private final ConcurrentHashMap<UUID, AttemptWindow> attempts = new ConcurrentHashMap<>();
    private final Clock clock;

    public MfaAttemptGuard(org.springframework.beans.factory.ObjectProvider<Clock> clock) {
        this.clock = clock.getIfAvailable(Clock::systemUTC);
    }

    public boolean isAllowed(UUID userId) {
        AttemptWindow current = attempts.get(userId);
        return current == null || current.expiresAt().isBefore(clock.instant()) || current.failures() < MAX_FAILURES;
    }

    public void recordFailure(UUID userId) {
        Instant now = clock.instant();
        attempts.compute(userId, (ignored, current) -> {
            if (current == null || current.expiresAt().isBefore(now)) {
                return new AttemptWindow(1, now.plus(WINDOW));
            }
            return new AttemptWindow(current.failures() + 1, current.expiresAt());
        });
    }

    public void reset(UUID userId) {
        attempts.remove(userId);
    }

    private record AttemptWindow(int failures, Instant expiresAt) {
    }
}

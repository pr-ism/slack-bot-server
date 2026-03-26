package com.slack.bot.application.worker;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;

public class AdaptivePollingBackoff {

    private final long baseDelayMs;
    private final long capDelayMs;
    private final RandomSource randomSource;

    private long nextUpperBoundMs;
    private int consecutiveEmptyPolls;
    private Thread ownerThread;

    public AdaptivePollingBackoff(Duration baseDelay, Duration capDelay) {
        this(baseDelay, capDelay, boundExclusive -> ThreadLocalRandom.current().nextLong(boundExclusive));
    }

    AdaptivePollingBackoff(
            Duration baseDelay,
            Duration capDelay,
            RandomSource randomSource
    ) {
        this.baseDelayMs = validateDelay(baseDelay, "baseDelay");
        this.capDelayMs = validateDelay(capDelay, "capDelay");
        if (capDelayMs < baseDelayMs) {
            throw new IllegalArgumentException("capDelay는 baseDelay보다 작을 수 없습니다.");
        }
        if (randomSource == null) {
            throw new IllegalArgumentException("randomSource는 null일 수 없습니다.");
        }

        this.randomSource = randomSource;
        consecutiveEmptyPolls = 0;
        nextUpperBoundMs = baseDelayMs;
    }

    Duration nextDelayAfterEmptyPoll() {
        claimOwnership();
        long currentUpperBoundMs = nextUpperBoundMs;
        consecutiveEmptyPolls++;
        nextUpperBoundMs = Math.min(capDelayMs, safeDouble(currentUpperBoundMs));

        return Duration.ofMillis(randomSource.nextLong(currentUpperBoundMs + 1L));
    }

    void reset() {
        claimOwnership();
        consecutiveEmptyPolls = 0;
        nextUpperBoundMs = baseDelayMs;
    }

    void releaseOwnership() {
        Thread currentThread = Thread.currentThread();
        synchronized (this) {
            if (ownerThread == null) {
                return;
            }
            if (ownerThread != currentThread) {
                throw new IllegalStateException("AdaptivePollingBackoff는 소유한 스레드만 해제할 수 있습니다.");
            }

            ownerThread = null;
        }
    }

    int consecutiveEmptyPolls() {
        claimOwnership();
        return consecutiveEmptyPolls;
    }

    Duration nextUpperBound() {
        claimOwnership();
        return Duration.ofMillis(nextUpperBoundMs);
    }

    private long validateDelay(Duration delay, String fieldName) {
        if (delay == null) {
            throw new IllegalArgumentException(fieldName + "는 null일 수 없습니다.");
        }

        long delayMs = delay.toMillis();
        if (delayMs <= 0) {
            throw new IllegalArgumentException(fieldName + "는 0보다 커야 합니다.");
        }

        return delayMs;
    }

    private long safeDouble(long value) {
        if (value > Long.MAX_VALUE / 2L) {
            return Long.MAX_VALUE;
        }

        return value * 2L;
    }

    private void claimOwnership() {
        Thread currentThread = Thread.currentThread();

        synchronized (this) {
            if (ownerThread == null) {
                ownerThread = currentThread;
                return;
            }
            if (ownerThread != currentThread) {
                throw new IllegalStateException("AdaptivePollingBackoff는 단일 poller 스레드에서만 접근할 수 있습니다.");
            }
        }
    }

    @FunctionalInterface
    interface RandomSource {
        long nextLong(long boundExclusive);
    }
}

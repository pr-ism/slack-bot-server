package com.slack.bot.application.interaction.box.retry;

import org.springframework.retry.RetryContext;
import org.springframework.retry.backoff.BackOffContext;
import org.springframework.retry.backoff.BackOffInterruptedException;
import org.springframework.retry.backoff.ExponentialBackOffPolicy;
import org.springframework.retry.backoff.Sleeper;
import org.springframework.retry.backoff.ThreadWaitSleeper;

public class EqualJitterExponentialBackOffPolicy extends ExponentialBackOffPolicy {

    private final RandomSource randomSource;
    private Sleeper sleeper;

    public EqualJitterExponentialBackOffPolicy() {
        this(new ThreadLocalRandomSource());
    }

    EqualJitterExponentialBackOffPolicy(RandomSource randomSource) {
        validateRandomSource(randomSource);

        this.randomSource = randomSource;
        this.sleeper = new ThreadWaitSleeper();
    }

    @Override
    public void setSleeper(Sleeper sleeper) {
        validateSleeper(sleeper);

        super.setSleeper(sleeper);
        this.sleeper = sleeper;
    }

    @Override
    public EqualJitterExponentialBackOffPolicy withSleeper(Sleeper sleeper) {
        EqualJitterExponentialBackOffPolicy backOffPolicy = newInstance();
        cloneValues(backOffPolicy);
        backOffPolicy.setSleeper(sleeper);
        return backOffPolicy;
    }

    @Override
    protected EqualJitterExponentialBackOffPolicy newInstance() {
        return new EqualJitterExponentialBackOffPolicy(randomSource);
    }

    @Override
    public BackOffContext start(RetryContext context) {
        return new EqualJitterBackOffContext(
                getInitialInterval(),
                getMultiplier(),
                getMaxInterval()
        );
    }

    @Override
    public void backOff(BackOffContext backOffContext) throws BackOffInterruptedException {
        EqualJitterBackOffContext context = (EqualJitterBackOffContext) backOffContext;
        long sleepMillis = context.nextSleepMillis();

        if (logger.isDebugEnabled()) {
            logger.debug("Sleeping for " + sleepMillis);
        }

        try {
            sleeper.sleep(sleepMillis);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new BackOffInterruptedException("Thread interrupted while sleeping", exception);
        }
    }

    private void validateRandomSource(RandomSource randomSource) {
        if (randomSource == null) {
            throw new IllegalArgumentException("randomSource는 비어 있을 수 없습니다.");
        }
    }

    private void validateSleeper(Sleeper sleeper) {
        if (sleeper == null) {
            throw new IllegalArgumentException("sleeper는 비어 있을 수 없습니다.");
        }
    }

    interface RandomSource {

        double nextDouble();
    }

    private static final class ThreadLocalRandomSource implements RandomSource {

        @Override
        public double nextDouble() {
            return java.util.concurrent.ThreadLocalRandom.current().nextDouble();
        }
    }

    private final class EqualJitterBackOffContext implements BackOffContext {

        private final double multiplier;
        private final long maxInterval;
        private long interval;

        private EqualJitterBackOffContext(
                long initialInterval,
                double multiplier,
                long maxInterval
        ) {
            this.interval = initialInterval;
            this.multiplier = multiplier;
            this.maxInterval = maxInterval;
        }

        private synchronized long nextSleepMillis() {
            long cappedInterval = resolveCappedInterval();
            updateNextInterval();
            return applyEqualJitter(cappedInterval);
        }

        private long resolveCappedInterval() {
            if (interval > maxInterval) {
                return maxInterval;
            }

            return interval;
        }

        private void updateNextInterval() {
            if (interval > maxInterval) {
                return;
            }

            double multipliedInterval = interval * multiplier;
            if (multipliedInterval >= Long.MAX_VALUE) {
                interval = Long.MAX_VALUE;
                return;
            }

            interval = (long) multipliedInterval;
        }

        private long applyEqualJitter(long cappedInterval) {
            long stablePortion = cappedInterval / 2;
            if (stablePortion == 0L) {
                stablePortion = 1L;
            }

            long jitterRange = cappedInterval - stablePortion;
            if (jitterRange == 0L) {
                return stablePortion;
            }

            return stablePortion + resolveRandomOffset(jitterRange);
        }

        private long resolveRandomOffset(long jitterRange) {
            double randomValue = randomSource.nextDouble();
            validateRandomValue(randomValue);

            return (long) Math.floor(randomValue * (jitterRange + 1.0d));
        }

        private void validateRandomValue(double randomValue) {
            if (randomValue < 0.0d || randomValue >= 1.0d) {
                throw new IllegalStateException("randomValue는 0.0 이상 1.0 미만이어야 합니다.");
            }
        }
    }
}

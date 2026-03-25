package com.slack.bot.application.worker;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntSupplier;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

@SuppressWarnings("NonAsciiCharacters")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class AdaptivePollingRunnerTest {

    @Test
    void empty_poll에만_adaptive_backoff가_적용된다() {
        // given
        RecordingPollingSleeper recordingPollingSleeper = new RecordingPollingSleeper();
        Queue<Integer> pollResults = new ArrayDeque<>(List.of(0, 0, 1, 0));
        AdaptivePollingRunner adaptivePollingRunner = new AdaptivePollingRunner(
                "test-runner",
                pollResults::remove,
                new AdaptivePollingBackoff(
                        Duration.ofMillis(100L),
                        Duration.ofMillis(250L),
                        boundExclusive -> boundExclusive - 1L
                ),
                recordingPollingSleeper,
                Duration.ofMillis(100L)
        );

        // when
        adaptivePollingRunner.runSingleCycle();
        adaptivePollingRunner.runSingleCycle();
        adaptivePollingRunner.runSingleCycle();
        adaptivePollingRunner.runSingleCycle();

        // then
        assertThat(recordingPollingSleeper.recordedSleeps()).containsExactly(
                Duration.ofMillis(100L),
                Duration.ofMillis(200L),
                Duration.ofMillis(100L)
        );
    }

    @Test
    void poll_error는_idle_backoff를_증가시키지_않고_base_delay로만_재시도한다() {
        // given
        RecordingPollingSleeper recordingPollingSleeper = new RecordingPollingSleeper();
        Queue<Object> pollResults = new ArrayDeque<>(List.of(new RuntimeException("db"), 0));
        AdaptivePollingRunner adaptivePollingRunner = new AdaptivePollingRunner(
                "test-runner",
                () -> {
                    Object result = pollResults.remove();
                    if (result instanceof RuntimeException runtimeException) {
                        throw runtimeException;
                    }
                    return (Integer) result;
                },
                new AdaptivePollingBackoff(
                        Duration.ofMillis(100L),
                        Duration.ofMillis(250L),
                        boundExclusive -> boundExclusive - 1L
                ),
                recordingPollingSleeper,
                Duration.ofMillis(100L)
        );

        // when
        adaptivePollingRunner.runSingleCycle();
        adaptivePollingRunner.runSingleCycle();

        // then
        assertThat(recordingPollingSleeper.recordedSleeps()).containsExactly(
                Duration.ofMillis(100L),
                Duration.ofMillis(100L)
        );
    }

    @Test
    void sleep중_notification_hint가_오면_조기_wakeup한다() throws Exception {
        // given
        CountDownLatch secondPollStarted = new CountDownLatch(1);
        AtomicInteger invocationCount = new AtomicInteger();
        IntSupplier pollOperation = () -> {
            int current = invocationCount.incrementAndGet();
            if (current >= 2) {
                secondPollStarted.countDown();
            }
            return 0;
        };
        AdaptivePollingRunner adaptivePollingRunner = new AdaptivePollingRunner(
                "wake-up-runner",
                Duration.ofSeconds(5L),
                Duration.ofSeconds(5L),
                pollOperation
        );

        try {
            // when
            adaptivePollingRunner.start();
            while (invocationCount.get() == 0) {
                Thread.onSpinWait();
            }
            adaptivePollingRunner.wakeUp();

            // then
            assertThat(secondPollStarted.await(500L, TimeUnit.MILLISECONDS)).isTrue();
        } finally {
            adaptivePollingRunner.stop();
        }
    }

    @Test
    void sleep중_interrupt가_발생해도_예외를_전파하지_않는다() {
        // given
        AdaptivePollingRunner adaptivePollingRunner = new AdaptivePollingRunner(
                "interrupt-runner",
                () -> 0,
                new AdaptivePollingBackoff(
                        Duration.ofMillis(100L),
                        Duration.ofMillis(100L),
                        boundExclusive -> 0L
                ),
                new InterruptingPollingSleeper(),
                Duration.ofMillis(100L)
        );

        // when
        adaptivePollingRunner.runSingleCycle();

        // then
        assertThat(Thread.currentThread().isInterrupted()).isTrue();
        Thread.interrupted();
    }

    @Test
    void stop은_not_running과_running_모두_처리한다() {
        // given
        AdaptivePollingRunner adaptivePollingRunner = new AdaptivePollingRunner(
                "stop-runner",
                Duration.ofMillis(100L),
                Duration.ofMillis(100L),
                () -> 0
        );

        // when
        adaptivePollingRunner.stop();
        adaptivePollingRunner.start();
        adaptivePollingRunner.stop();

        // then
        assertThat(adaptivePollingRunner.isRunning()).isFalse();
    }

    private static final class RecordingPollingSleeper implements AdaptivePollingRunner.PollingSleeper {

        private final List<Duration> recordedSleeps = new ArrayList<>();

        @Override
        public AdaptivePollingRunner.PollingSleepResult sleep(Duration delay) {
            recordedSleeps.add(delay);
            return AdaptivePollingRunner.PollingSleepResult.COMPLETED;
        }

        @Override
        public void wakeUp() {
        }

        private List<Duration> recordedSleeps() {
            return recordedSleeps;
        }
    }

    private static final class InterruptingPollingSleeper implements AdaptivePollingRunner.PollingSleeper {

        @Override
        public AdaptivePollingRunner.PollingSleepResult sleep(Duration delay) throws InterruptedException {
            throw new InterruptedException("interrupted");
        }

        @Override
        public void wakeUp() {
        }
    }
}

package com.slack.bot.application.interaction.box.retry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.retry.backoff.BackOffContext;
import org.springframework.retry.backoff.Sleeper;

@SuppressWarnings("NonAsciiCharacters")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class EqualJitterExponentialBackOffPolicyTest {

    @Test
    void equal_jitter는_상한의_절반부터_상한까지_분산된다() {
        // given
        EqualJitterExponentialBackOffPolicy backOffPolicy = new EqualJitterExponentialBackOffPolicy(
                new SequenceRandomSource(0.0d, 0.5d, 0.999d)
        );
        RecordingSleeper sleeper = new RecordingSleeper();
        backOffPolicy.setInitialInterval(100L);
        backOffPolicy.setMultiplier(2.0d);
        backOffPolicy.setMaxInterval(1_000L);
        backOffPolicy.setSleeper(sleeper);
        BackOffContext backOffContext = backOffPolicy.start(null);

        // when
        backOffPolicy.backOff(backOffContext);
        backOffPolicy.backOff(backOffContext);
        backOffPolicy.backOff(backOffContext);

        // then
        assertThat(sleeper.sleepMillis()).containsExactly(50L, 150L, 400L);
    }

    @Test
    void cap에_도달하면_equal_jitter_범위가_cap_기준으로_고정된다() {
        // given
        EqualJitterExponentialBackOffPolicy backOffPolicy = new EqualJitterExponentialBackOffPolicy(
                new SequenceRandomSource(0.0d, 0.0d, 0.0d)
        );
        RecordingSleeper sleeper = new RecordingSleeper();
        backOffPolicy.setInitialInterval(1_000L);
        backOffPolicy.setMultiplier(2.0d);
        backOffPolicy.setMaxInterval(1_000L);
        backOffPolicy.setSleeper(sleeper);
        BackOffContext backOffContext = backOffPolicy.start(null);

        // when
        backOffPolicy.backOff(backOffContext);
        backOffPolicy.backOff(backOffContext);
        backOffPolicy.backOff(backOffContext);

        // then
        assertThat(sleeper.sleepMillis()).containsExactly(500L, 500L, 500L);
    }

    @Test
    void random_value가_범위를_벗어나면_예외를_던진다() {
        // given
        EqualJitterExponentialBackOffPolicy backOffPolicy = new EqualJitterExponentialBackOffPolicy(
                new SequenceRandomSource(1.0d)
        );
        RecordingSleeper sleeper = new RecordingSleeper();
        backOffPolicy.setInitialInterval(100L);
        backOffPolicy.setMultiplier(2.0d);
        backOffPolicy.setMaxInterval(1_000L);
        backOffPolicy.setSleeper(sleeper);
        BackOffContext backOffContext = backOffPolicy.start(null);

        // when & then
        assertThatThrownBy(() -> backOffPolicy.backOff(backOffContext))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("randomValue는 0.0 이상 1.0 미만이어야 합니다.");
    }

    @Test
    void 기본_생성자와_withSleeper는_설정을_복제한_equal_jitter_정책을_반환한다() {
        // given
        EqualJitterExponentialBackOffPolicy backOffPolicy = new EqualJitterExponentialBackOffPolicy();
        RecordingSleeper sleeper = new RecordingSleeper();
        backOffPolicy.setInitialInterval(100L);
        backOffPolicy.setMultiplier(2.0d);
        backOffPolicy.setMaxInterval(1_000L);

        // when
        EqualJitterExponentialBackOffPolicy copiedPolicy = backOffPolicy.withSleeper(sleeper);
        BackOffContext backOffContext = copiedPolicy.start(null);
        copiedPolicy.backOff(backOffContext);

        // then
        assertThat(copiedPolicy).isNotSameAs(backOffPolicy);
        assertThat(copiedPolicy.getInitialInterval()).isEqualTo(100L);
        assertThat(copiedPolicy.getMultiplier()).isEqualTo(2.0d);
        assertThat(copiedPolicy.getMaxInterval()).isEqualTo(1_000L);
        assertThat(sleeper.sleepMillis()).singleElement().satisfies(value -> {
            assertThat(value).isGreaterThanOrEqualTo(50L);
            assertThat(value).isLessThanOrEqualTo(100L);
        });
    }

    @Test
    void random_source가_null이면_예외를_던진다() {
        // given

        // when & then
        assertThatThrownBy(() -> new EqualJitterExponentialBackOffPolicy(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("randomSource는 비어 있을 수 없습니다.");
    }

    @Test
    void sleeper가_null이면_예외를_던진다() {
        // given
        EqualJitterExponentialBackOffPolicy backOffPolicy = new EqualJitterExponentialBackOffPolicy(
                new SequenceRandomSource(0.0d)
        );

        // when & then
        assertThatThrownBy(() -> backOffPolicy.setSleeper(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("sleeper는 비어 있을 수 없습니다.");
    }

    @Test
    void start는_initial_interval_supplier를_즉시_평가하지_않는다() {
        // given
        EqualJitterExponentialBackOffPolicy backOffPolicy = new EqualJitterExponentialBackOffPolicy(
                new SequenceRandomSource(0.0d)
        );
        RecordingSleeper sleeper = new RecordingSleeper();
        AtomicLong initialInterval = new AtomicLong(100L);
        AtomicInteger invocationCount = new AtomicInteger();
        backOffPolicy.initialIntervalSupplier(() -> {
            invocationCount.incrementAndGet();
            return initialInterval.get();
        });
        backOffPolicy.setSleeper(sleeper);
        BackOffContext backOffContext = backOffPolicy.start(null);
        initialInterval.set(200L);

        // when
        backOffPolicy.backOff(backOffContext);

        // then
        assertThat(invocationCount.get()).isEqualTo(1);
        assertThat(sleeper.sleepMillis()).containsExactly(100L);
    }

    @Test
    void multiplier_supplier와_max_interval_supplier를_백오프마다_다시_읽는다() {
        // given
        EqualJitterExponentialBackOffPolicy backOffPolicy = new EqualJitterExponentialBackOffPolicy(
                new SequenceRandomSource(0.0d, 0.0d, 0.0d)
        );
        RecordingSleeper sleeper = new RecordingSleeper();
        AtomicLong initialInterval = new AtomicLong(100L);
        AtomicLong maxInterval = new AtomicLong(1_000L);
        AtomicInteger multiplierScale = new AtomicInteger(20);
        backOffPolicy.initialIntervalSupplier(initialInterval::get);
        backOffPolicy.multiplierSupplier(() -> multiplierScale.get() / 10.0d);
        backOffPolicy.maxIntervalSupplier(maxInterval::get);
        backOffPolicy.setSleeper(sleeper);
        BackOffContext backOffContext = backOffPolicy.start(null);

        // when
        backOffPolicy.backOff(backOffContext);
        multiplierScale.set(30);
        backOffPolicy.backOff(backOffContext);
        maxInterval.set(250L);
        backOffPolicy.backOff(backOffContext);

        // then
        assertThat(sleeper.sleepMillis()).containsExactly(50L, 100L, 125L);
    }

    private static final class SequenceRandomSource implements EqualJitterExponentialBackOffPolicy.RandomSource {

        private final double[] values;
        private int index;

        private SequenceRandomSource(double... values) {
            this.values = values;
        }

        @Override
        public double nextDouble() {
            double value = values[index];
            index++;
            return value;
        }
    }

    private static final class RecordingSleeper implements Sleeper {

        private final List<Long> sleepMillis = new ArrayList<>();

        @Override
        public void sleep(long backOffPeriod) {
            sleepMillis.add(backOffPeriod);
        }

        private List<Long> sleepMillis() {
            return sleepMillis;
        }
    }
}

package com.slack.bot.application.worker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

@SuppressWarnings("NonAsciiCharacters")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class AdaptivePollingBackoffTest {

    @Test
    void empty_poll이_연속되면_full_jitter_upper_bound가_exponential_backoff_with_cap으로_증가한다() {
        // given
        List<Long> bounds = new ArrayList<>();
        AdaptivePollingBackoff adaptivePollingBackoff = new AdaptivePollingBackoff(
                Duration.ofMillis(100L),
                Duration.ofMillis(250L),
                boundExclusive -> {
                    bounds.add(boundExclusive);
                    return boundExclusive - 1L;
                }
        );

        // when
        Duration firstDelay = adaptivePollingBackoff.nextDelayAfterEmptyPoll();
        Duration secondDelay = adaptivePollingBackoff.nextDelayAfterEmptyPoll();
        Duration thirdDelay = adaptivePollingBackoff.nextDelayAfterEmptyPoll();
        Duration fourthDelay = adaptivePollingBackoff.nextDelayAfterEmptyPoll();

        // then
        assertThat(bounds).containsExactly(101L, 201L, 251L, 251L);
        assertThat(firstDelay).isEqualTo(Duration.ofMillis(100L));
        assertThat(secondDelay).isEqualTo(Duration.ofMillis(200L));
        assertThat(thirdDelay).isEqualTo(Duration.ofMillis(250L));
        assertThat(fourthDelay).isEqualTo(Duration.ofMillis(250L));
        assertThat(adaptivePollingBackoff.consecutiveEmptyPolls()).isEqualTo(4);
        assertThat(adaptivePollingBackoff.nextUpperBound()).isEqualTo(Duration.ofMillis(250L));
    }

    @Test
    void work를_찾으면_backoff가_즉시_reset된다() {
        // given
        AdaptivePollingBackoff adaptivePollingBackoff = new AdaptivePollingBackoff(
                Duration.ofMillis(100L),
                Duration.ofMillis(250L),
                boundExclusive -> boundExclusive - 1L
        );
        adaptivePollingBackoff.nextDelayAfterEmptyPoll();
        adaptivePollingBackoff.nextDelayAfterEmptyPoll();

        // when
        adaptivePollingBackoff.reset();

        // then
        assertThat(adaptivePollingBackoff.consecutiveEmptyPolls()).isZero();
        assertThat(adaptivePollingBackoff.nextUpperBound()).isEqualTo(Duration.ofMillis(100L));
    }

    @Test
    void cap이_base보다_작으면_예외가_발생한다() {
        assertThatThrownBy(() -> new AdaptivePollingBackoff(Duration.ofMillis(200L), Duration.ofMillis(100L)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("capDelay는 baseDelay보다 작을 수 없습니다.");
    }

    @Test
    void delay가_null이거나_0이하면_예외가_발생한다() {
        assertThatThrownBy(() -> new AdaptivePollingBackoff(null, Duration.ofMillis(100L)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("baseDelay는 null일 수 없습니다.");
        assertThatThrownBy(() -> new AdaptivePollingBackoff(Duration.ZERO, Duration.ofMillis(100L)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("baseDelay는 0보다 커야 합니다.");
    }

    @Test
    void upper_bound_double이_overflow되면_long_max로_보호된다() {
        // given
        AdaptivePollingBackoff adaptivePollingBackoff = new AdaptivePollingBackoff(
                Duration.ofMillis(Long.MAX_VALUE / 2L + 1L),
                Duration.ofMillis(Long.MAX_VALUE),
                boundExclusive -> 0L
        );

        // when
        adaptivePollingBackoff.nextDelayAfterEmptyPoll();

        // then
        assertThat(adaptivePollingBackoff.nextUpperBound()).isEqualTo(Duration.ofMillis(Long.MAX_VALUE));
    }
}

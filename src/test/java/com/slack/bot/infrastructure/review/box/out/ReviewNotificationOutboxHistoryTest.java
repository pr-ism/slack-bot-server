package com.slack.bot.infrastructure.review.box.out;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

import com.slack.bot.infrastructure.common.FailureSnapshotDefaults;
import com.slack.bot.infrastructure.interaction.box.SlackInteractionFailureType;
import java.time.Instant;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

@SuppressWarnings("NonAsciiCharacters")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class ReviewNotificationOutboxHistoryTest {

    @Test
    void completed는_outboxId가_null이어도_history를_생성한다() {
        // when
        ReviewNotificationOutboxHistory history = ReviewNotificationOutboxHistory.completed(
                null,
                1,
                ReviewNotificationOutboxStatus.RETRY_PENDING,
                Instant.parse("2026-03-27T00:00:00Z"),
                "failure",
                SlackInteractionFailureType.NONE
        );

        // then
        assertAll(
                () -> assertThat(history.getOutboxId()).isNull(),
                () -> assertThat(history.getProcessingAttempt()).isEqualTo(1),
                () -> assertThat(history.getStatus()).isEqualTo(ReviewNotificationOutboxStatus.RETRY_PENDING)
        );
    }

    @Test
    void bindOutboxId는_null_outboxId를_실제_id로_바인딩한다() {
        // given
        ReviewNotificationOutboxHistory history = ReviewNotificationOutboxHistory.completed(
                null,
                1,
                ReviewNotificationOutboxStatus.FAILED,
                Instant.parse("2026-03-27T00:00:00Z"),
                "failure",
                SlackInteractionFailureType.RETRY_EXHAUSTED
        );

        // when
        ReviewNotificationOutboxHistory actual = history.bindOutboxId(10L);

        // then
        assertAll(
                () -> assertThat(actual.getOutboxId()).isEqualTo(10L),
                () -> assertThat(actual.getStatus()).isEqualTo(ReviewNotificationOutboxStatus.FAILED),
                () -> assertThat(actual.getFailureType()).isEqualTo(SlackInteractionFailureType.RETRY_EXHAUSTED)
        );
    }

    @Test
    void bindOutboxId는_다른_id로_변경할_수_없다() {
        // given
        ReviewNotificationOutboxHistory history = ReviewNotificationOutboxHistory.completed(
                10L,
                1,
                ReviewNotificationOutboxStatus.SENT,
                Instant.parse("2026-03-27T00:00:00Z"),
                FailureSnapshotDefaults.NO_FAILURE_REASON,
                SlackInteractionFailureType.NONE
        );

        // when & then
        assertThatThrownBy(() -> history.bindOutboxId(11L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("history outboxId를 다른 값으로 변경할 수 없습니다.");
    }

    @Test
    void FAILED_history는_failureType이_null이면_생성할_수_없다() {
        // when & then
        assertThatThrownBy(() -> ReviewNotificationOutboxHistory.completed(
                10L,
                1,
                ReviewNotificationOutboxStatus.FAILED,
                Instant.parse("2026-03-27T00:00:00Z"),
                "failure",
                null
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("FAILED history에는 failureType이 필요합니다.");
    }

    @Test
    void RETRY_PENDING_history는_PROCESSING_TIMEOUT_failureType을_허용한다() {
        // when
        ReviewNotificationOutboxHistory history = ReviewNotificationOutboxHistory.completed(
                10L,
                1,
                ReviewNotificationOutboxStatus.RETRY_PENDING,
                Instant.parse("2026-03-27T00:00:00Z"),
                "timeout",
                SlackInteractionFailureType.PROCESSING_TIMEOUT
        );

        // then
        assertAll(
                () -> assertThat(history.getStatus()).isEqualTo(ReviewNotificationOutboxStatus.RETRY_PENDING),
                () -> assertThat(history.getFailureType()).isEqualTo(SlackInteractionFailureType.PROCESSING_TIMEOUT)
        );
    }
}

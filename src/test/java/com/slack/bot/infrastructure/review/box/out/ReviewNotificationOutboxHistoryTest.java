package com.slack.bot.infrastructure.review.box.out;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

import com.slack.bot.infrastructure.common.BoxFailureSnapshot;
import com.slack.bot.infrastructure.interaction.box.SlackInteractionFailureType;
import java.time.Instant;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

@SuppressWarnings("NonAsciiCharacters")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class ReviewNotificationOutboxHistoryTest {

    @Test
    void completed는_할당된_outboxId로_history를_생성한다() {
        // when
        ReviewNotificationOutboxHistory history = ReviewNotificationOutboxHistory.completed(
                10L,
                1,
                ReviewNotificationOutboxStatus.RETRY_PENDING,
                Instant.parse("2026-03-27T00:00:00Z"),
                BoxFailureSnapshot.present("failure", SlackInteractionFailureType.RETRYABLE)
        );

        // then
        assertAll(
                () -> assertThat(history.getOutboxId()).isEqualTo(10L),
                () -> assertThat(history.getProcessingAttempt()).isEqualTo(1),
                () -> assertThat(history.getStatus()).isEqualTo(ReviewNotificationOutboxStatus.RETRY_PENDING),
                () -> assertThat(history.getFailure().type()).isEqualTo(SlackInteractionFailureType.RETRYABLE)
        );
    }

    @Test
    void completed는_outboxId가_null이면_예외를_던진다() {
        // when & then
        assertThatThrownBy(() -> ReviewNotificationOutboxHistory.completed(
                null,
                1,
                ReviewNotificationOutboxStatus.RETRY_PENDING,
                Instant.parse("2026-03-27T00:00:00Z"),
                BoxFailureSnapshot.present("failure", SlackInteractionFailureType.RETRYABLE)
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("outboxId는 비어 있을 수 없습니다.");
    }

    @Test
    void FAILED_history는_failureType이_null이면_생성할_수_없다() {
        // when & then
        assertThatThrownBy(() -> ReviewNotificationOutboxHistory.completed(
                10L,
                1,
                ReviewNotificationOutboxStatus.FAILED,
                Instant.parse("2026-03-27T00:00:00Z"),
                BoxFailureSnapshot.present("failure", null)
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("failureType은 비어 있을 수 없습니다.");
    }

    @Test
    void RETRY_PENDING_history는_PROCESSING_TIMEOUT_failureType을_허용한다() {
        // when
        ReviewNotificationOutboxHistory history = ReviewNotificationOutboxHistory.completed(
                10L,
                1,
                ReviewNotificationOutboxStatus.RETRY_PENDING,
                Instant.parse("2026-03-27T00:00:00Z"),
                BoxFailureSnapshot.present("timeout", SlackInteractionFailureType.PROCESSING_TIMEOUT)
        );

        // then
        assertAll(
                () -> assertThat(history.getStatus()).isEqualTo(ReviewNotificationOutboxStatus.RETRY_PENDING),
                () -> assertThat(history.getFailure().type()).isEqualTo(SlackInteractionFailureType.PROCESSING_TIMEOUT)
        );
    }

    @Test
    void SENT_history는_실패정보가_없어야_한다() {
        // when
        ReviewNotificationOutboxHistory history = ReviewNotificationOutboxHistory.completed(
                10L,
                1,
                ReviewNotificationOutboxStatus.SENT,
                Instant.parse("2026-03-27T00:00:00Z"),
                BoxFailureSnapshot.absent()
        );

        // then
        assertThat(history.getFailure().isPresent()).isFalse();
    }
}

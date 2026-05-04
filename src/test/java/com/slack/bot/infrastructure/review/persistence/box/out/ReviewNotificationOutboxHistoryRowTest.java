package com.slack.bot.infrastructure.review.persistence.box.out;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

import com.slack.bot.infrastructure.common.BoxFailureSnapshot;
import com.slack.bot.infrastructure.interaction.box.SlackInteractionFailureType;
import com.slack.bot.infrastructure.review.box.out.ReviewNotificationOutboxHistory;
import com.slack.bot.infrastructure.review.box.out.ReviewNotificationOutboxStatus;
import java.time.Instant;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

@SuppressWarnings("NonAsciiCharacters")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class ReviewNotificationOutboxHistoryRowTest {

    @Test
    void from은_SENT_history의_absent_failure를_row와_domain에_그대로_반영한다() {
        // given
        ReviewNotificationOutboxHistory sentHistory = ReviewNotificationOutboxHistory.rehydrate(
                1L,
                10L,
                1,
                ReviewNotificationOutboxStatus.SENT,
                Instant.parse("2026-04-07T00:01:00Z"),
                BoxFailureSnapshot.absent()
        );

        // when
        ReviewNotificationOutboxHistoryRow row = ReviewNotificationOutboxHistoryRow.from(sentHistory);
        ReviewNotificationOutboxHistory restoredHistory = row.toDomain();

        // then
        assertAll(
                () -> assertThat(row.getId()).isEqualTo(sentHistory.getId()),
                () -> assertThat(row.getOutboxId()).isEqualTo(sentHistory.getOutboxId()),
                () -> assertThat(row.getProcessingAttempt()).isEqualTo(sentHistory.getProcessingAttempt()),
                () -> assertThat(row.getStatus()).isEqualTo(ReviewNotificationOutboxStatus.SENT),
                () -> assertThat(row.getCompletedAt()).isEqualTo(sentHistory.getCompletedAt()),
                () -> assertThat(row.getFailureReason()).isNull(),
                () -> assertThat(row.getFailureType()).isNull(),
                () -> assertThat(restoredHistory.getFailure().isPresent()).isFalse()
        );
    }

    @Test
    void from은_FAILED_history의_failure를_row와_domain에_그대로_반영한다() {
        // given
        ReviewNotificationOutboxHistory failedHistory = ReviewNotificationOutboxHistory.rehydrate(
                1L,
                10L,
                1,
                ReviewNotificationOutboxStatus.FAILED,
                Instant.parse("2026-04-07T00:02:00Z"),
                BoxFailureSnapshot.present("failure", SlackInteractionFailureType.RETRY_EXHAUSTED)
        );

        // when
        ReviewNotificationOutboxHistoryRow row = ReviewNotificationOutboxHistoryRow.from(failedHistory);
        ReviewNotificationOutboxHistory restoredHistory = row.toDomain();

        // then
        assertAll(
                () -> assertThat(row.getFailureReason()).isEqualTo("failure"),
                () -> assertThat(row.getFailureType()).isEqualTo(SlackInteractionFailureType.RETRY_EXHAUSTED),
                () -> assertThat(restoredHistory.getStatus()).isEqualTo(ReviewNotificationOutboxStatus.FAILED),
                () -> assertThat(restoredHistory.getFailure().reason()).isEqualTo("failure"),
                () -> assertThat(restoredHistory.getFailure().type())
                        .isEqualTo(SlackInteractionFailureType.RETRY_EXHAUSTED)
        );
    }

    @Test
    void toDomain은_blank_failure_reason을_상태_오류로_변환한다() {
        // given
        ReviewNotificationOutboxHistoryRow row = new ReviewNotificationOutboxHistoryRow(
                1L,
                10L,
                1,
                ReviewNotificationOutboxStatus.FAILED,
                Instant.parse("2026-04-07T00:02:00Z"),
                " ",
                SlackInteractionFailureType.RETRY_EXHAUSTED
        );

        // when & then
        assertThatThrownBy(() -> row.toDomain())
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("history 상태가 올바르지 않습니다.")
                .hasCauseInstanceOf(IllegalArgumentException.class);
    }
}

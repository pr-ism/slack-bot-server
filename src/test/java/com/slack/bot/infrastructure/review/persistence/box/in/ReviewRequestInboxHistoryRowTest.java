package com.slack.bot.infrastructure.review.persistence.box.in;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

import com.slack.bot.infrastructure.common.BoxFailureSnapshot;
import com.slack.bot.infrastructure.review.box.in.ReviewRequestInboxFailureType;
import com.slack.bot.infrastructure.review.box.in.ReviewRequestInboxHistory;
import com.slack.bot.infrastructure.review.box.in.ReviewRequestInboxStatus;
import java.time.Instant;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

@SuppressWarnings("NonAsciiCharacters")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class ReviewRequestInboxHistoryRowTest {

    @Test
    void from은_처리완료_history의_absent_failure를_row와_domain에_그대로_반영한다() {
        // given
        ReviewRequestInboxHistory processedHistory = ReviewRequestInboxHistory.rehydrate(
                1L,
                10L,
                1,
                ReviewRequestInboxStatus.PROCESSED,
                Instant.parse("2026-02-24T00:01:00Z"),
                BoxFailureSnapshot.absent()
        );

        // when
        ReviewRequestInboxHistoryRow row = ReviewRequestInboxHistoryRow.from(processedHistory);
        ReviewRequestInboxHistory restoredHistory = row.toDomain();

        // then
        assertAll(
                () -> assertThat(row.getId()).isEqualTo(processedHistory.getId()),
                () -> assertThat(row.getInboxId()).isEqualTo(processedHistory.getInboxId()),
                () -> assertThat(row.getProcessingAttempt()).isEqualTo(processedHistory.getProcessingAttempt()),
                () -> assertThat(row.getStatus()).isEqualTo(ReviewRequestInboxStatus.PROCESSED),
                () -> assertThat(row.getCompletedAt()).isEqualTo(processedHistory.getCompletedAt()),
                () -> assertThat(row.getFailureReason()).isNull(),
                () -> assertThat(row.getFailureType()).isNull(),
                () -> assertThat(restoredHistory.getFailure().isPresent()).isFalse()
        );
    }

    @Test
    void from은_실패_history의_failure를_row와_domain에_그대로_반영한다() {
        // given
        ReviewRequestInboxHistory failedHistory = ReviewRequestInboxHistory.rehydrate(
                1L,
                10L,
                1,
                ReviewRequestInboxStatus.FAILED,
                Instant.parse("2026-02-24T00:02:00Z"),
                BoxFailureSnapshot.present("failure", ReviewRequestInboxFailureType.RETRY_EXHAUSTED)
        );

        // when
        ReviewRequestInboxHistoryRow row = ReviewRequestInboxHistoryRow.from(failedHistory);
        ReviewRequestInboxHistory restoredHistory = row.toDomain();

        // then
        assertAll(
                () -> assertThat(row.getFailureReason()).isEqualTo("failure"),
                () -> assertThat(row.getFailureType()).isEqualTo(ReviewRequestInboxFailureType.RETRY_EXHAUSTED),
                () -> assertThat(restoredHistory.getStatus()).isEqualTo(ReviewRequestInboxStatus.FAILED),
                () -> assertThat(restoredHistory.getFailure().reason()).isEqualTo("failure"),
                () -> assertThat(restoredHistory.getFailure().type())
                        .isEqualTo(ReviewRequestInboxFailureType.RETRY_EXHAUSTED)
        );
    }

    @Test
    void toDomain은_blank_failure_reason을_상태_오류로_변환한다() {
        // given
        ReviewRequestInboxHistoryRow row = new ReviewRequestInboxHistoryRow(
                1L,
                10L,
                1,
                ReviewRequestInboxStatus.FAILED,
                Instant.parse("2026-02-24T00:02:00Z"),
                " ",
                ReviewRequestInboxFailureType.RETRY_EXHAUSTED
        );

        // when & then
        assertThatThrownBy(() -> row.toDomain())
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("history 상태가 올바르지 않습니다.")
                .hasCauseInstanceOf(IllegalArgumentException.class);
    }
}

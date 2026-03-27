package com.slack.bot.infrastructure.review.box.in;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

import com.slack.bot.infrastructure.common.FailureSnapshotDefaults;
import java.time.Instant;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

@SuppressWarnings("NonAsciiCharacters")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class ReviewRequestInboxHistoryTest {

    @Test
    void completed는_inboxId가_null이어도_history를_생성한다() {
        // when
        ReviewRequestInboxHistory history = ReviewRequestInboxHistory.completed(
                null,
                1,
                ReviewRequestInboxStatus.RETRY_PENDING,
                Instant.parse("2026-03-27T00:00:00Z"),
                "failure",
                ReviewRequestInboxFailureType.NONE
        );

        // then
        assertAll(
                () -> assertThat(history.getInboxId()).isNull(),
                () -> assertThat(history.getProcessingAttempt()).isEqualTo(1),
                () -> assertThat(history.getStatus()).isEqualTo(ReviewRequestInboxStatus.RETRY_PENDING)
        );
    }

    @Test
    void bindInboxId는_null_inboxId를_실제_id로_바인딩한다() {
        // given
        ReviewRequestInboxHistory history = ReviewRequestInboxHistory.completed(
                null,
                1,
                ReviewRequestInboxStatus.FAILED,
                Instant.parse("2026-03-27T00:00:00Z"),
                "failure",
                ReviewRequestInboxFailureType.NON_RETRYABLE
        );

        // when
        ReviewRequestInboxHistory actual = history.bindInboxId(10L);

        // then
        assertAll(
                () -> assertThat(actual.getInboxId()).isEqualTo(10L),
                () -> assertThat(actual.getStatus()).isEqualTo(ReviewRequestInboxStatus.FAILED),
                () -> assertThat(actual.getFailureType()).isEqualTo(ReviewRequestInboxFailureType.NON_RETRYABLE)
        );
    }

    @Test
    void bindInboxId는_다른_id로_변경할_수_없다() {
        // given
        ReviewRequestInboxHistory history = ReviewRequestInboxHistory.completed(
                10L,
                1,
                ReviewRequestInboxStatus.PROCESSED,
                Instant.parse("2026-03-27T00:00:00Z"),
                FailureSnapshotDefaults.NO_FAILURE_REASON,
                ReviewRequestInboxFailureType.NONE
        );

        // when & then
        assertThatThrownBy(() -> history.bindInboxId(11L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("history inboxId를 다른 값으로 변경할 수 없습니다.");
    }
}

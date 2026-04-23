package com.slack.bot.infrastructure.review.persistence.box.in;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import com.slack.bot.infrastructure.common.BoxEventTime;
import com.slack.bot.infrastructure.common.BoxFailureSnapshot;
import com.slack.bot.infrastructure.common.BoxProcessingLease;
import com.slack.bot.infrastructure.review.box.in.ReviewRequestInbox;
import com.slack.bot.infrastructure.review.box.in.ReviewRequestInboxFailureType;
import com.slack.bot.infrastructure.review.box.in.ReviewRequestInboxStatus;
import java.time.Instant;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

@SuppressWarnings("NonAsciiCharacters")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class ReviewRequestInboxRowTest {

    @Test
    void from은_대기_상태의_absent_상세값을_row와_domain에_그대로_반영한다() {
        // given
        ReviewRequestInbox pendingInbox = ReviewRequestInbox.rehydrate(
                1L,
                "api-key:42",
                "api-key",
                42L,
                "{\"githubPullRequestId\":42}",
                Instant.parse("2026-02-24T00:00:00Z"),
                ReviewRequestInboxStatus.PENDING,
                0,
                BoxProcessingLease.idle(),
                BoxEventTime.absent(),
                BoxEventTime.absent(),
                BoxFailureSnapshot.absent()
        );

        // when
        ReviewRequestInboxRow row = ReviewRequestInboxRow.from(pendingInbox);
        ReviewRequestInbox restoredInbox = row.toDomain();

        // then
        assertAll(
                () -> assertThat(row.getId()).isEqualTo(pendingInbox.getId()),
                () -> assertThat(row.getIdempotencyKey()).isEqualTo(pendingInbox.getIdempotencyKey()),
                () -> assertThat(row.getStatus()).isEqualTo(ReviewRequestInboxStatus.PENDING),
                () -> assertThat(row.getProcessingStartedAt()).isNull(),
                () -> assertThat(row.getProcessedAt()).isNull(),
                () -> assertThat(row.getFailedAt()).isNull(),
                () -> assertThat(row.getFailureReason()).isNull(),
                () -> assertThat(row.getFailureType()).isNull(),
                () -> assertThat(restoredInbox.hasClaimedProcessingLease()).isFalse(),
                () -> assertThat(restoredInbox.getFailure().isPresent()).isFalse()
        );
    }

    @Test
    void from은_실패_상태의_failure_상세값을_row와_domain에_그대로_반영한다() {
        // given
        Instant failedAt = Instant.parse("2026-02-24T00:15:00Z");
        ReviewRequestInbox failedInbox = ReviewRequestInbox.rehydrate(
                1L,
                "api-key:43",
                "api-key",
                43L,
                "{\"githubPullRequestId\":43}",
                Instant.parse("2026-02-24T00:00:00Z"),
                ReviewRequestInboxStatus.FAILED,
                1,
                BoxProcessingLease.idle(),
                BoxEventTime.absent(),
                BoxEventTime.present(failedAt),
                BoxFailureSnapshot.present("failure", ReviewRequestInboxFailureType.NON_RETRYABLE)
        );

        // when
        ReviewRequestInboxRow row = ReviewRequestInboxRow.from(failedInbox);
        ReviewRequestInbox restoredInbox = row.toDomain();

        // then
        assertAll(
                () -> assertThat(row.getFailedAt()).isEqualTo(failedAt),
                () -> assertThat(row.getFailureReason()).isEqualTo("failure"),
                () -> assertThat(row.getFailureType()).isEqualTo(ReviewRequestInboxFailureType.NON_RETRYABLE),
                () -> assertThat(restoredInbox.getStatus()).isEqualTo(ReviewRequestInboxStatus.FAILED),
                () -> assertThat(restoredInbox.getFailedTime().occurredAt()).isEqualTo(failedAt),
                () -> assertThat(restoredInbox.getFailure().reason()).isEqualTo("failure"),
                () -> assertThat(restoredInbox.getFailure().type())
                        .isEqualTo(ReviewRequestInboxFailureType.NON_RETRYABLE)
        );
    }
}

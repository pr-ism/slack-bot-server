package com.slack.bot.infrastructure.review.persistence.box.out;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import com.slack.bot.infrastructure.common.BoxEventTime;
import com.slack.bot.infrastructure.common.BoxFailureSnapshot;
import com.slack.bot.infrastructure.common.BoxProcessingLease;
import com.slack.bot.infrastructure.interaction.box.SlackInteractionFailureType;
import com.slack.bot.infrastructure.review.box.out.ReviewNotificationOutbox;
import com.slack.bot.infrastructure.review.box.out.ReviewNotificationOutboxMessageType;
import com.slack.bot.infrastructure.review.box.out.ReviewNotificationOutboxProjectId;
import com.slack.bot.infrastructure.review.box.out.ReviewNotificationOutboxStatus;
import com.slack.bot.infrastructure.review.box.out.ReviewNotificationOutboxStringField;
import java.time.Instant;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

@SuppressWarnings("NonAsciiCharacters")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class ReviewNotificationOutboxRowTest {

    @Test
    void from은_semantic_PENDING_outbox의_absent_상세값을_row와_domain에_그대로_반영한다() {
        // given
        ReviewNotificationOutbox pendingOutbox = ReviewNotificationOutbox.rehydrate(
                1L,
                ReviewNotificationOutboxMessageType.SEMANTIC,
                "review-row-semantic",
                ReviewNotificationOutboxProjectId.present(1001L),
                "T1",
                "C1",
                ReviewNotificationOutboxStringField.present("{\"payload\":\"hello\"}"),
                ReviewNotificationOutboxStringField.absent(),
                ReviewNotificationOutboxStringField.absent(),
                ReviewNotificationOutboxStringField.absent(),
                ReviewNotificationOutboxStatus.PENDING,
                0,
                BoxProcessingLease.idle(),
                BoxEventTime.absent(),
                BoxEventTime.absent(),
                BoxFailureSnapshot.absent()
        );

        // when
        ReviewNotificationOutboxRow row = ReviewNotificationOutboxRow.from(pendingOutbox);
        ReviewNotificationOutbox restoredOutbox = row.toDomain();

        // then
        assertAll(
                () -> assertThat(row.getId()).isEqualTo(pendingOutbox.getId()),
                () -> assertThat(row.getMessageType()).isEqualTo(ReviewNotificationOutboxMessageType.SEMANTIC),
                () -> assertThat(row.getProjectId()).isEqualTo(1001L),
                () -> assertThat(row.getProcessingStartedAt()).isNull(),
                () -> assertThat(row.getSentAt()).isNull(),
                () -> assertThat(row.getFailedAt()).isNull(),
                () -> assertThat(row.getFailureReason()).isNull(),
                () -> assertThat(row.getFailureType()).isNull(),
                () -> assertThat(restoredOutbox.getStatus()).isEqualTo(ReviewNotificationOutboxStatus.PENDING),
                () -> assertThat(restoredOutbox.hasSemanticPayload()).isTrue(),
                () -> assertThat(restoredOutbox.getFailure().isPresent()).isFalse()
        );
    }

    @Test
    void from은_failed_channel_blocks_outbox의_완료_상세값을_row와_domain에_그대로_반영한다() {
        // given
        Instant failedAt = Instant.parse("2026-04-07T00:15:00Z");
        ReviewNotificationOutbox failedOutbox = ReviewNotificationOutbox.rehydrate(
                2L,
                ReviewNotificationOutboxMessageType.CHANNEL_BLOCKS,
                "review-row-failed",
                ReviewNotificationOutboxProjectId.absent(),
                "T1",
                "C1",
                ReviewNotificationOutboxStringField.absent(),
                ReviewNotificationOutboxStringField.present("[{\"type\":\"section\"}]"),
                ReviewNotificationOutboxStringField.absent(),
                ReviewNotificationOutboxStringField.present("fallback"),
                ReviewNotificationOutboxStatus.FAILED,
                2,
                BoxProcessingLease.idle(),
                BoxEventTime.absent(),
                BoxEventTime.present(failedAt),
                BoxFailureSnapshot.present("failure", SlackInteractionFailureType.RETRY_EXHAUSTED)
        );

        // when
        ReviewNotificationOutboxRow row = ReviewNotificationOutboxRow.from(failedOutbox);
        ReviewNotificationOutbox restoredOutbox = row.toDomain();

        // then
        assertAll(
                () -> assertThat(row.getProjectId()).isNull(),
                () -> assertThat(row.getFailedAt()).isEqualTo(failedAt),
                () -> assertThat(row.getFailureReason()).isEqualTo("failure"),
                () -> assertThat(row.getFailureType()).isEqualTo(SlackInteractionFailureType.RETRY_EXHAUSTED),
                () -> assertThat(restoredOutbox.getStatus()).isEqualTo(ReviewNotificationOutboxStatus.FAILED),
                () -> assertThat(restoredOutbox.getFailedTime().occurredAt()).isEqualTo(failedAt),
                () -> assertThat(restoredOutbox.getFailure().reason()).isEqualTo("failure"),
                () -> assertThat(restoredOutbox.getFailure().type())
                        .isEqualTo(SlackInteractionFailureType.RETRY_EXHAUSTED)
        );
    }
}

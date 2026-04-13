package com.slack.bot.infrastructure.review.persistence.box.out;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import com.slack.bot.application.IntegrationTest;
import com.slack.bot.infrastructure.common.BoxEventTime;
import com.slack.bot.infrastructure.common.BoxFailureSnapshot;
import com.slack.bot.infrastructure.common.BoxProcessingLease;
import com.slack.bot.infrastructure.interaction.box.SlackInteractionFailureType;
import com.slack.bot.infrastructure.review.box.out.ReviewNotificationOutbox;
import com.slack.bot.infrastructure.review.box.out.ReviewNotificationOutboxHistory;
import com.slack.bot.infrastructure.review.box.out.ReviewNotificationOutboxMessageType;
import com.slack.bot.infrastructure.review.box.out.ReviewNotificationOutboxProjectId;
import com.slack.bot.infrastructure.review.box.out.ReviewNotificationOutboxStatus;
import com.slack.bot.infrastructure.review.box.out.ReviewNotificationOutboxStringField;
import com.slack.bot.infrastructure.review.box.out.repository.ReviewNotificationOutboxRepository;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@IntegrationTest
@SuppressWarnings("NonAsciiCharacters")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class ReviewNotificationOutboxRepositoryAdapterTest {

    @Autowired
    ReviewNotificationOutboxRepository reviewNotificationOutboxRepository;

    @Autowired
    JpaReviewNotificationOutboxRepository jpaReviewNotificationOutboxRepository;

    @Autowired
    JpaReviewNotificationOutboxHistoryRepository jpaReviewNotificationOutboxHistoryRepository;

    @Test
    void 완료된_review_outbox와_history를_같이_삭제한다() {
        // given
        ReviewNotificationOutbox oldSentOutbox = createSentOutbox(
                "cleanup-review-sent",
                Instant.parse("2026-02-24T00:01:00Z")
        );
        ReviewNotificationOutbox recentFailedOutbox = createFailedOutbox(
                "cleanup-review-failed",
                Instant.parse("2026-02-24T00:09:00Z")
        );

        // when
        int deletedCount = reviewNotificationOutboxRepository.deleteCompletedBefore(
                Instant.parse("2026-02-24T00:05:00Z"),
                100
        );

        // then
        List<ReviewNotificationOutboxHistory> histories = jpaReviewNotificationOutboxHistoryRepository.findAllDomains();
        assertAll(
                () -> assertThat(deletedCount).isEqualTo(1),
                () -> assertThat(jpaReviewNotificationOutboxRepository.findDomainById(oldSentOutbox.getId())).isEmpty(),
                () -> assertThat(jpaReviewNotificationOutboxRepository.findDomainById(recentFailedOutbox.getId())).isPresent(),
                () -> assertThat(histories)
                        .filteredOn(history -> history.getOutboxId().equals(oldSentOutbox.getId()))
                        .isEmpty(),
                () -> assertThat(histories)
                        .filteredOn(history -> history.getOutboxId().equals(recentFailedOutbox.getId()))
                        .hasSize(1)
        );
    }

    @Test
    void deleteCompletedBefore는_batch_크기만큼만_삭제한다() {
        // given
        ReviewNotificationOutbox firstOutbox = createSentOutbox(
                "cleanup-review-batch-1",
                Instant.parse("2026-02-24T00:01:00Z")
        );
        ReviewNotificationOutbox secondOutbox = createSentOutbox(
                "cleanup-review-batch-2",
                Instant.parse("2026-02-24T00:02:00Z")
        );

        // when
        int deletedCount = reviewNotificationOutboxRepository.deleteCompletedBefore(
                Instant.parse("2026-02-24T00:05:00Z"),
                1
        );

        // then
        assertAll(
                () -> assertThat(deletedCount).isEqualTo(1),
                () -> assertThat(jpaReviewNotificationOutboxRepository.findDomainById(firstOutbox.getId())).isEmpty(),
                () -> assertThat(jpaReviewNotificationOutboxRepository.findDomainById(secondOutbox.getId())).isPresent()
        );
    }

    private ReviewNotificationOutbox createSentOutbox(String idempotencyKey, Instant sentAt) {
        ReviewNotificationOutbox pendingOutbox = ReviewNotificationOutbox.semantic(
                idempotencyKey,
                1001L,
                "T1",
                "C1",
                "{\"payload\":\"hello\"}"
        );
        ReviewNotificationOutbox savedOutbox = reviewNotificationOutboxRepository.save(pendingOutbox);
        ReviewNotificationOutbox completedOutbox = ReviewNotificationOutbox.rehydrate(
                savedOutbox.getId(),
                ReviewNotificationOutboxMessageType.SEMANTIC,
                idempotencyKey,
                ReviewNotificationOutboxProjectId.present(1001L),
                "T1",
                "C1",
                ReviewNotificationOutboxStringField.present("{\"payload\":\"hello\"}"),
                ReviewNotificationOutboxStringField.absent(),
                ReviewNotificationOutboxStringField.absent(),
                ReviewNotificationOutboxStringField.absent(),
                ReviewNotificationOutboxStatus.SENT,
                1,
                BoxProcessingLease.idle(),
                BoxEventTime.present(sentAt),
                BoxEventTime.absent(),
                BoxFailureSnapshot.absent()
        );
        ReviewNotificationOutboxHistory history = ReviewNotificationOutboxHistory.completed(
                savedOutbox.getId(),
                1,
                ReviewNotificationOutboxStatus.SENT,
                sentAt,
                BoxFailureSnapshot.absent()
        );
        return saveOutboxWithHistory(completedOutbox, history);
    }

    private ReviewNotificationOutbox createFailedOutbox(String idempotencyKey, Instant failedAt) {
        ReviewNotificationOutbox pendingOutbox = ReviewNotificationOutbox.semantic(
                idempotencyKey,
                1002L,
                "T1",
                "C1",
                "{\"payload\":\"failed\"}"
        );
        ReviewNotificationOutbox savedOutbox = reviewNotificationOutboxRepository.save(pendingOutbox);
        ReviewNotificationOutbox completedOutbox = ReviewNotificationOutbox.rehydrate(
                savedOutbox.getId(),
                ReviewNotificationOutboxMessageType.SEMANTIC,
                idempotencyKey,
                ReviewNotificationOutboxProjectId.present(1002L),
                "T1",
                "C1",
                ReviewNotificationOutboxStringField.present("{\"payload\":\"failed\"}"),
                ReviewNotificationOutboxStringField.absent(),
                ReviewNotificationOutboxStringField.absent(),
                ReviewNotificationOutboxStringField.absent(),
                ReviewNotificationOutboxStatus.FAILED,
                1,
                BoxProcessingLease.idle(),
                BoxEventTime.absent(),
                BoxEventTime.present(failedAt),
                BoxFailureSnapshot.present("failure", SlackInteractionFailureType.BUSINESS_INVARIANT)
        );
        ReviewNotificationOutboxHistory history = ReviewNotificationOutboxHistory.completed(
                savedOutbox.getId(),
                1,
                ReviewNotificationOutboxStatus.FAILED,
                failedAt,
                BoxFailureSnapshot.present("failure", SlackInteractionFailureType.BUSINESS_INVARIANT)
        );
        return saveOutboxWithHistory(completedOutbox, history);
    }

    private ReviewNotificationOutbox saveOutboxWithHistory(
            ReviewNotificationOutbox outbox,
            ReviewNotificationOutboxHistory history
    ) {
        ReviewNotificationOutbox savedOutbox = reviewNotificationOutboxRepository.save(outbox);
        ReviewNotificationOutboxHistoryJpaEntity entity = new ReviewNotificationOutboxHistoryJpaEntity();
        entity.apply(history);
        jpaReviewNotificationOutboxHistoryRepository.save(entity);
        return savedOutbox;
    }
}

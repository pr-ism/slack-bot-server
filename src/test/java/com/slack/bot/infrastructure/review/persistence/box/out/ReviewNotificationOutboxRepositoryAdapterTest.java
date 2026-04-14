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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

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

    @Autowired
    NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    @Autowired
    PlatformTransactionManager transactionManager;

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

    @Test
    void 잠겨있는_완료된_review_outbox는_cleanup에서_건너뛴다() throws Exception {
        // given
        ReviewNotificationOutbox lockedOutbox = createSentOutbox(
                "cleanup-review-locked",
                Instant.parse("2026-02-24T00:01:00Z")
        );
        CountDownLatch lockAcquired = new CountDownLatch(1);
        CountDownLatch releaseLock = new CountDownLatch(1);

        ExecutorService executorService = Executors.newFixedThreadPool(2);
        try {
            Future<?> lockFuture = executorService.submit(() -> {
                new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                    namedParameterJdbcTemplate.query(
                            """
                            SELECT id
                            FROM review_notification_outbox
                            WHERE id = :outboxId
                            FOR UPDATE
                            """,
                            new MapSqlParameterSource().addValue("outboxId", lockedOutbox.getId()),
                            (resultSet, rowNum) -> resultSet.getLong(1)
                    );
                    lockAcquired.countDown();
                    awaitLatch(releaseLock);
                });
            });
            awaitLatch(lockAcquired);

            // when
            Future<Integer> cleanupFuture = executorService.submit(() -> reviewNotificationOutboxRepository.deleteCompletedBefore(
                    Instant.parse("2026-02-24T00:05:00Z"),
                    100
            ));
            int deletedCount = cleanupFuture.get(1, TimeUnit.SECONDS);

            // then
            List<ReviewNotificationOutboxHistory> histories = jpaReviewNotificationOutboxHistoryRepository.findAllDomains();
            assertAll(
                    () -> assertThat(deletedCount).isZero(),
                    () -> assertThat(jpaReviewNotificationOutboxRepository.findDomainById(lockedOutbox.getId())).isPresent(),
                    () -> assertThat(histories)
                            .filteredOn(history -> history.getOutboxId().equals(lockedOutbox.getId()))
                            .hasSize(1)
            );

            releaseLock.countDown();
            lockFuture.get(5, TimeUnit.SECONDS);
        } finally {
            releaseLock.countDown();
            executorService.shutdownNow();
        }
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

    private void awaitLatch(CountDownLatch latch) {
        try {
            boolean completed = latch.await(5, TimeUnit.SECONDS);
            if (!completed) {
                throw new IllegalStateException("락 대기 중 시간이 초과되었습니다.");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("락 대기 중 인터럽트가 발생했습니다.", exception);
        }
    }
}

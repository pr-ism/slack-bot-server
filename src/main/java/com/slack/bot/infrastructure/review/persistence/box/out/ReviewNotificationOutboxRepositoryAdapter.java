package com.slack.bot.infrastructure.review.persistence.box.out;

import static com.slack.bot.infrastructure.review.box.out.QReviewNotificationOutbox.reviewNotificationOutbox;

import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.slack.bot.infrastructure.common.MysqlDuplicateKeyDetector;
import com.slack.bot.infrastructure.interaction.box.SlackInteractivityFailureType;
import com.slack.bot.infrastructure.review.box.out.ReviewNotificationOutbox;
import com.slack.bot.infrastructure.review.box.out.ReviewNotificationOutboxStatus;
import com.slack.bot.infrastructure.review.box.out.repository.ReviewNotificationOutboxRepository;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@RequiredArgsConstructor
public class ReviewNotificationOutboxRepositoryAdapter implements ReviewNotificationOutboxRepository {

    private static final List<ReviewNotificationOutboxStatus> CLAIMABLE_STATUSES = List.of(
            ReviewNotificationOutboxStatus.PENDING,
            ReviewNotificationOutboxStatus.RETRY_PENDING
    );

    private final JPAQueryFactory queryFactory;
    private final MysqlDuplicateKeyDetector mysqlDuplicateKeyDetector;
    private final ReviewNotificationOutboxCreator outboxCreator;
    private final JpaReviewNotificationOutboxRepository repository;

    @Override
    public boolean enqueue(ReviewNotificationOutbox outbox) {
        try {
            outboxCreator.saveNew(outbox);
            return true;
        } catch (DataIntegrityViolationException exception) {
            if (mysqlDuplicateKeyDetector.isNotDuplicateKey(exception)) {
                throw exception;
            }

            return false;
        }
    }

    @Override
    @Transactional
    public ReviewNotificationOutbox save(ReviewNotificationOutbox outbox) {
        return repository.save(outbox);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ReviewNotificationOutbox> findById(Long outboxId) {
        return repository.findById(outboxId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ReviewNotificationOutbox> findClaimable(int limit) {
        if (limit <= 0) {
            return Collections.emptyList();
        }

        return queryFactory.selectFrom(reviewNotificationOutbox)
                           .where(reviewNotificationOutbox.status.in(CLAIMABLE_STATUSES))
                           .orderBy(reviewNotificationOutbox.id.asc())
                           .limit(limit)
                           .fetch();
    }

    @Override
    @Transactional
    public boolean markProcessingIfClaimable(Long outboxId, Instant processingStartedAt) {
        validateOutboxId(outboxId);
        validateProcessingStartedAt(processingStartedAt);

        long updatedCount = queryFactory.update(reviewNotificationOutbox)
                                        .set(reviewNotificationOutbox.status, ReviewNotificationOutboxStatus.PROCESSING)
                                        .set(reviewNotificationOutbox.processingStartedAt, processingStartedAt)
                                        .set(
                                                reviewNotificationOutbox.processingAttempt,
                                                reviewNotificationOutbox.processingAttempt.add(1)
                                        )
                                        .set(reviewNotificationOutbox.failedAt, Expressions.nullExpression(Instant.class))
                                        .set(
                                                reviewNotificationOutbox.failureReason,
                                                Expressions.nullExpression(String.class)
                                        )
                                        .set(
                                                reviewNotificationOutbox.failureType,
                                                Expressions.nullExpression(SlackInteractivityFailureType.class)
                                        )
                                        .where(
                                                reviewNotificationOutbox.id.eq(outboxId),
                                                reviewNotificationOutbox.status.in(CLAIMABLE_STATUSES)
                                        )
                                        .execute();

        return updatedCount > 0;
    }

    @Override
    @Transactional
    public int recoverTimeoutProcessing(Instant processingStartedBefore, Instant failedAt, String failureReason) {
        validateProcessingStartedBefore(processingStartedBefore);
        validateFailedAt(failedAt);
        validateFailureReason(failureReason);

        return Math.toIntExact(queryFactory.update(reviewNotificationOutbox)
                                         .set(reviewNotificationOutbox.status, ReviewNotificationOutboxStatus.RETRY_PENDING)
                                         .set(
                                                 reviewNotificationOutbox.processingStartedAt,
                                                 Expressions.nullExpression(Instant.class)
                                         )
                                         .set(reviewNotificationOutbox.failedAt, failedAt)
                                         .set(reviewNotificationOutbox.failureReason, failureReason)
                                         .set(
                                                 reviewNotificationOutbox.failureType,
                                                 Expressions.nullExpression(SlackInteractivityFailureType.class)
                                         )
                                         .where(
                                                 reviewNotificationOutbox.status.eq(ReviewNotificationOutboxStatus.PROCESSING),
                                                 reviewNotificationOutbox.processingStartedAt.isNull()
                                                                                           .or(reviewNotificationOutbox.processingStartedAt.lt(
                                                                                                   processingStartedBefore
                                                                                           ))
                                         )
                                         .execute());
    }

    private void validateOutboxId(Long outboxId) {
        if (outboxId == null) {
            throw new IllegalArgumentException("outboxId는 비어 있을 수 없습니다.");
        }
    }

    private void validateProcessingStartedAt(Instant processingStartedAt) {
        if (processingStartedAt == null) {
            throw new IllegalArgumentException("processingStartedAt은 비어 있을 수 없습니다.");
        }
    }

    private void validateProcessingStartedBefore(Instant processingStartedBefore) {
        if (processingStartedBefore == null) {
            throw new IllegalArgumentException("processingStartedBefore는 비어 있을 수 없습니다.");
        }
    }

    private void validateFailedAt(Instant failedAt) {
        if (failedAt == null) {
            throw new IllegalArgumentException("failedAt은 비어 있을 수 없습니다.");
        }
    }

    private void validateFailureReason(String failureReason) {
        if (failureReason == null || failureReason.isBlank()) {
            throw new IllegalArgumentException("failureReason은 비어 있을 수 없습니다.");
        }
    }
}

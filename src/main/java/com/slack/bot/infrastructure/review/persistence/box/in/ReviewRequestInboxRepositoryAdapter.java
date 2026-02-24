package com.slack.bot.infrastructure.review.persistence.box.in;

import static com.slack.bot.infrastructure.review.box.in.QReviewRequestInbox.reviewRequestInbox;

import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.slack.bot.infrastructure.common.MysqlDuplicateKeyDetector;
import com.slack.bot.infrastructure.review.box.in.ReviewRequestInboxFailureType;
import com.slack.bot.infrastructure.review.box.in.ReviewRequestInbox;
import com.slack.bot.infrastructure.review.box.in.ReviewRequestInboxStatus;
import com.slack.bot.infrastructure.review.box.in.repository.ReviewRequestInboxRepository;
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
public class ReviewRequestInboxRepositoryAdapter implements ReviewRequestInboxRepository {

    private static final List<ReviewRequestInboxStatus> CLAIMABLE_STATUSES = List.of(
            ReviewRequestInboxStatus.PENDING,
            ReviewRequestInboxStatus.RETRY_PENDING
    );

    private final JPAQueryFactory queryFactory;
    private final MysqlDuplicateKeyDetector mysqlDuplicateKeyDetector;
    private final ReviewRequestInboxCreator reviewRequestInboxCreator;
    private final JpaReviewRequestInboxRepository reviewRequestInboxJpaRepository;

    @Override
    @Transactional
    public void upsertPending(
            String coalescingKey,
            String apiKey,
            Long githubPullRequestId,
            String requestJson,
            Instant availableAt
    ) {
        validateCoalescingKey(coalescingKey);
        validateApiKey(apiKey);
        validateGithubPullRequestId(githubPullRequestId);
        validateRequestJson(requestJson);
        validateAvailableAt(availableAt);

        long updatedCount = updatePending(coalescingKey, apiKey, githubPullRequestId, requestJson, availableAt);
        if (updatedCount > 0) {
            return;
        }

        ReviewRequestInbox inbox = ReviewRequestInbox.pending(
                coalescingKey,
                apiKey,
                githubPullRequestId,
                requestJson,
                availableAt
        );

        try {
            reviewRequestInboxCreator.saveNew(inbox);
        } catch (DataIntegrityViolationException exception) {
            if (mysqlDuplicateKeyDetector.isNotDuplicateKey(exception)) {
                throw exception;
            }

            updatePending(coalescingKey, apiKey, githubPullRequestId, requestJson, availableAt);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<ReviewRequestInbox> findClaimable(Instant availableBeforeOrAt, int limit) {
        validateAvailableAt(availableBeforeOrAt);

        if (limit <= 0) {
            return Collections.emptyList();
        }

        return queryFactory.selectFrom(reviewRequestInbox)
                           .where(
                                   reviewRequestInbox.status.in(CLAIMABLE_STATUSES),
                                   reviewRequestInbox.availableAt.loe(availableBeforeOrAt)
                           )
                           .orderBy(reviewRequestInbox.availableAt.asc(), reviewRequestInbox.id.asc())
                           .limit(limit)
                           .fetch();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ReviewRequestInbox> findById(Long inboxId) {
        return reviewRequestInboxJpaRepository.findById(inboxId);
    }

    @Override
    @Transactional
    public boolean markProcessingIfClaimable(Long inboxId, Instant processingStartedAt, Instant availableBeforeOrAt) {
        validateInboxId(inboxId);
        validateProcessingStartedAt(processingStartedAt);
        validateAvailableAt(availableBeforeOrAt);

        long updatedCount = queryFactory.update(reviewRequestInbox)
                                        .set(reviewRequestInbox.status, ReviewRequestInboxStatus.PROCESSING)
                                        .set(reviewRequestInbox.processingAttempt,
                                                reviewRequestInbox.processingAttempt.add(1)
                                        )
                                        .set(reviewRequestInbox.processingStartedAt, processingStartedAt)
                                        .set(reviewRequestInbox.failedAt, Expressions.nullExpression(Instant.class))
                                        .set(reviewRequestInbox.failureReason, Expressions.nullExpression(String.class))
                                        .set(
                                                reviewRequestInbox.failureType,
                                                Expressions.nullExpression(ReviewRequestInboxFailureType.class)
                                        )
                                        .where(
                                                reviewRequestInbox.id.eq(inboxId),
                                                reviewRequestInbox.status.in(CLAIMABLE_STATUSES),
                                                reviewRequestInbox.availableAt.loe(availableBeforeOrAt)
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

        return Math.toIntExact(queryFactory.update(reviewRequestInbox)
                                         .set(reviewRequestInbox.status, ReviewRequestInboxStatus.RETRY_PENDING)
                                         .set(
                                                 reviewRequestInbox.processingStartedAt,
                                                 Expressions.nullExpression(Instant.class)
                                         )
                                         .set(reviewRequestInbox.failedAt, failedAt)
                                         .set(reviewRequestInbox.failureReason, failureReason)
                                         .set(
                                                 reviewRequestInbox.failureType,
                                                 Expressions.nullExpression(ReviewRequestInboxFailureType.class)
                                         )
                                         .where(
                                                 reviewRequestInbox.status.eq(ReviewRequestInboxStatus.PROCESSING),
                                                 reviewRequestInbox.processingStartedAt.isNull()
                                                                                      .or(reviewRequestInbox.processingStartedAt.lt(
                                                                                              processingStartedBefore
                                                                                      ))
                                         )
                                         .execute());
    }

    @Override
    @Transactional
    public ReviewRequestInbox save(ReviewRequestInbox inbox) {
        return reviewRequestInboxJpaRepository.save(inbox);
    }

    private long updatePending(
            String coalescingKey,
            String apiKey,
            Long githubPullRequestId,
            String requestJson,
            Instant availableAt
    ) {
        return queryFactory.update(reviewRequestInbox)
                           .set(reviewRequestInbox.apiKey, apiKey)
                           .set(reviewRequestInbox.githubPullRequestId, githubPullRequestId)
                           .set(reviewRequestInbox.requestJson, requestJson)
                           .set(reviewRequestInbox.availableAt, availableAt)
                           .set(reviewRequestInbox.status, ReviewRequestInboxStatus.PENDING)
                           .set(reviewRequestInbox.processingAttempt, 0)
                           .set(
                                   reviewRequestInbox.processingStartedAt,
                                   Expressions.nullExpression(Instant.class)
                           )
                           .set(reviewRequestInbox.processedAt, Expressions.nullExpression(Instant.class))
                           .set(reviewRequestInbox.failedAt, Expressions.nullExpression(Instant.class))
                           .set(reviewRequestInbox.failureReason, Expressions.nullExpression(String.class))
                           .set(
                                   reviewRequestInbox.failureType,
                                   Expressions.nullExpression(ReviewRequestInboxFailureType.class)
                           )
                           .where(reviewRequestInbox.coalescingKey.eq(coalescingKey))
                           .execute();
    }

    private void validateInboxId(Long inboxId) {
        if (inboxId == null) {
            throw new IllegalArgumentException("inboxId는 비어 있을 수 없습니다.");
        }
    }

    private void validateCoalescingKey(String coalescingKey) {
        if (coalescingKey == null || coalescingKey.isBlank()) {
            throw new IllegalArgumentException("coalescingKey는 비어 있을 수 없습니다.");
        }
    }

    private void validateApiKey(String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException("apiKey는 비어 있을 수 없습니다.");
        }
    }

    private void validateGithubPullRequestId(Long githubPullRequestId) {
        if (githubPullRequestId == null || githubPullRequestId <= 0) {
            throw new IllegalArgumentException("githubPullRequestId는 비어 있을 수 없습니다.");
        }
    }

    private void validateRequestJson(String requestJson) {
        if (requestJson == null || requestJson.isBlank()) {
            throw new IllegalArgumentException("requestJson은 비어 있을 수 없습니다.");
        }
    }

    private void validateAvailableAt(Instant availableAt) {
        if (availableAt == null) {
            throw new IllegalArgumentException("availableAt은 비어 있을 수 없습니다.");
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

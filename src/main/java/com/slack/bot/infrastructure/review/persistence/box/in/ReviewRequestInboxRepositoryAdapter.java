package com.slack.bot.infrastructure.review.persistence.box.in;

import static com.slack.bot.infrastructure.review.box.in.QReviewRequestInbox.reviewRequestInbox;

import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.slack.bot.infrastructure.common.MysqlDuplicateKeyDetector;
import com.slack.bot.infrastructure.review.box.in.ReviewRequestInboxFailureType;
import com.slack.bot.infrastructure.review.box.in.ReviewRequestInbox;
import com.slack.bot.infrastructure.review.box.in.ReviewRequestInboxStatus;
import com.slack.bot.infrastructure.review.box.in.repository.ReviewRequestInboxRepository;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
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
    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;
    private final MysqlDuplicateKeyDetector mysqlDuplicateKeyDetector;
    private final ReviewRequestInboxCreator reviewRequestInboxCreator;
    private final JpaReviewRequestInboxRepository reviewRequestInboxJpaRepository;

    @Override
    @Transactional
    public void upsertPending(
            String idempotencyKey,
            String apiKey,
            Long githubPullRequestId,
            String requestJson,
            Instant availableAt
    ) {
        validateIdempotencyKey(idempotencyKey);
        validateApiKey(apiKey);
        validateGithubPullRequestId(githubPullRequestId);
        validateRequestJson(requestJson);
        validateAvailableAt(availableAt);

        ReviewRequestInbox inbox = ReviewRequestInbox.pending(
                idempotencyKey,
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

            updatePending(idempotencyKey, apiKey, githubPullRequestId, requestJson, availableAt);
        }
    }

    @Override
    @Transactional
    public Optional<Long> claimNextId(
            Instant processingStartedAt,
            Instant availableBeforeOrAt,
            Collection<Long> excludedInboxIds
    ) {
        validateProcessingStartedAt(processingStartedAt);
        validateAvailableAt(availableBeforeOrAt);

        MapSqlParameterSource selectParameters = new MapSqlParameterSource()
                .addValue(
                        "claimableStatuses",
                        List.of(
                                ReviewRequestInboxStatus.PENDING.name(),
                                ReviewRequestInboxStatus.RETRY_PENDING.name()
                        )
                )
                .addValue("availableBeforeOrAt", Timestamp.from(availableBeforeOrAt));
        addExcludedInboxIds(selectParameters, excludedInboxIds);

        List<Long> claimedIds = namedParameterJdbcTemplate.query(
                buildClaimNextIdSelectSql(excludedInboxIds),
                selectParameters,
                (resultSet, rowNum) -> resultSet.getLong(1)
        );
        if (claimedIds.isEmpty()) {
            return Optional.empty();
        }

        Long inboxId = claimedIds.getFirst();
        MapSqlParameterSource updateParameters = new MapSqlParameterSource()
                .addValue("processingStatus", ReviewRequestInboxStatus.PROCESSING.name())
                .addValue("processingStartedAt", Timestamp.from(processingStartedAt))
                .addValue("inboxId", inboxId);

        int updatedCount = namedParameterJdbcTemplate.update(
                """
                    UPDATE review_request_inbox
                    SET status = :processingStatus,
                        processing_attempt = processing_attempt + 1,
                        processing_started_at = :processingStartedAt,
                        failed_at = NULL,
                        failure_reason = NULL,
                        failure_type = NULL
                    WHERE id = :inboxId
                    """,
                updateParameters
        );
        if (updatedCount == 0) {
            return Optional.empty();
        }

        return Optional.of(inboxId);
    }

    private String buildClaimNextIdSelectSql(Collection<Long> excludedInboxIds) {
        StringBuilder sql = new StringBuilder(
                """
                SELECT id
                FROM review_request_inbox
                WHERE status IN (:claimableStatuses)
                  AND available_at <= :availableBeforeOrAt
                """
        );

        appendExcludedInboxIdsClause(sql, excludedInboxIds);
        sql.append(
                """
                ORDER BY available_at ASC, id ASC
                LIMIT 1
                FOR UPDATE SKIP LOCKED
                """
        );

        return sql.toString();
    }

    private void appendExcludedInboxIdsClause(StringBuilder sql, Collection<Long> excludedInboxIds) {
        if (excludedInboxIds == null || excludedInboxIds.isEmpty()) {
            return;
        }

        sql.append("\n  AND id NOT IN (:excludedInboxIds)");
    }

    private void addExcludedInboxIds(
            MapSqlParameterSource parameters,
            Collection<Long> excludedInboxIds
    ) {
        if (excludedInboxIds == null || excludedInboxIds.isEmpty()) {
            return;
        }

        parameters.addValue("excludedInboxIds", excludedInboxIds);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ReviewRequestInbox> findById(Long inboxId) {
        return reviewRequestInboxJpaRepository.findById(inboxId);
    }

    @Override
    @Transactional
    public int recoverTimeoutProcessing(
            Instant processingStartedBefore,
            Instant failedAt,
            String failureReason,
            int maxAttempts
    ) {
        validateProcessingStartedBefore(processingStartedBefore);
        validateFailedAt(failedAt);
        validateFailureReason(failureReason);
        validateMaxAttempts(maxAttempts);

        BooleanExpression timeoutCondition = reviewRequestInbox.processingStartedAt.isNull()
                                                                                     .or(reviewRequestInbox.processingStartedAt.lt(
                                                                                             processingStartedBefore
                                                                                     ));

        long exhaustedCount = queryFactory.update(reviewRequestInbox)
                                          .set(reviewRequestInbox.status, ReviewRequestInboxStatus.FAILED)
                                          .set(
                                                  reviewRequestInbox.processingStartedAt,
                                                  Expressions.nullExpression(Instant.class)
                                          )
                                          .set(reviewRequestInbox.failedAt, failedAt)
                                          .set(reviewRequestInbox.failureReason, failureReason)
                                          .set(
                                                  reviewRequestInbox.failureType,
                                                  ReviewRequestInboxFailureType.RETRY_EXHAUSTED
                                          )
                                          .where(
                                                  reviewRequestInbox.status.eq(ReviewRequestInboxStatus.PROCESSING),
                                                  timeoutCondition,
                                                  reviewRequestInbox.processingAttempt.goe(maxAttempts)
                                          )
                                          .execute();

        long recoveredCount = queryFactory.update(reviewRequestInbox)
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
                                                  timeoutCondition,
                                                  reviewRequestInbox.processingAttempt.lt(maxAttempts)
                                          )
                                          .execute();

        return Math.toIntExact(exhaustedCount + recoveredCount);
    }

    @Override
    @Transactional
    public ReviewRequestInbox save(ReviewRequestInbox inbox) {
        return reviewRequestInboxJpaRepository.save(inbox);
    }

    private long updatePending(
            String idempotencyKey,
            String apiKey,
            Long githubPullRequestId,
            String requestJson,
            Instant availableAt
    ) {
        // PROCESSING / PROCESSED / FAILED는 현재 워커 전이를 보호하기 위해 재큐잉으로 덮어쓰지 않음
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
                           .where(
                                   reviewRequestInbox.idempotencyKey.eq(idempotencyKey),
                                   reviewRequestInbox.status.in(CLAIMABLE_STATUSES)
                           )
                           .execute();
    }

    private void validateIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("idempotencyKey는 비어 있을 수 없습니다.");
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

    private void validateMaxAttempts(int maxAttempts) {
        if (maxAttempts <= 0) {
            throw new IllegalArgumentException("maxAttempts는 0보다 커야 합니다.");
        }
    }
}

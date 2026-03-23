package com.slack.bot.infrastructure.review.persistence.box.out;

import static com.slack.bot.infrastructure.review.box.out.QReviewNotificationOutbox.reviewNotificationOutbox;

import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.slack.bot.infrastructure.interaction.box.SlackInteractionFailureType;
import com.slack.bot.infrastructure.review.box.out.ReviewNotificationOutbox;
import com.slack.bot.infrastructure.review.box.out.ReviewNotificationOutboxStatus;
import com.slack.bot.infrastructure.review.box.out.repository.ReviewNotificationOutboxRepository;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@RequiredArgsConstructor
public class ReviewNotificationOutboxRepositoryAdapter implements ReviewNotificationOutboxRepository {

    private final JPAQueryFactory queryFactory;
    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;
    private final JpaReviewNotificationOutboxRepository repository;

    @Override
    public boolean enqueue(ReviewNotificationOutbox outbox) {
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("idempotencyKey", outbox.getIdempotencyKey())
                .addValue("projectId", outbox.getProjectId())
                .addValue("teamId", outbox.getTeamId())
                .addValue("channelId", outbox.getChannelId())
                .addValue("payloadJson", outbox.getPayloadJson())
                .addValue("blocksJson", outbox.getBlocksJson())
                .addValue("attachmentsJson", outbox.getAttachmentsJson())
                .addValue("fallbackText", outbox.getFallbackText())
                .addValue("pendingStatus", outbox.getStatus().name())
                .addValue("processingAttempt", outbox.getProcessingAttempt());

        int updatedCount = namedParameterJdbcTemplate.update(
                buildEnqueueSql(),
                parameters
        );

        return updatedCount > 0;
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
    @Transactional
    public Optional<Long> claimNextId(Instant processingStartedAt, Collection<Long> excludedOutboxIds) {
        validateProcessingStartedAt(processingStartedAt);

        MapSqlParameterSource selectParameters = new MapSqlParameterSource()
                .addValue(
                        "claimableStatuses",
                        List.of(
                                ReviewNotificationOutboxStatus.PENDING.name(),
                                ReviewNotificationOutboxStatus.RETRY_PENDING.name()
                        )
                );
        addExcludedOutboxIds(selectParameters, excludedOutboxIds);

        List<Long> claimedIds = namedParameterJdbcTemplate.query(
                buildClaimNextIdSelectSql(excludedOutboxIds),
                selectParameters,
                (resultSet, rowNum) -> resultSet.getLong(1)
        );
        if (claimedIds.isEmpty()) {
            return Optional.empty();
        }

        Long outboxId = claimedIds.getFirst();
        MapSqlParameterSource updateParameters = new MapSqlParameterSource()
                .addValue("processingStatus", ReviewNotificationOutboxStatus.PROCESSING.name())
                .addValue("processingStartedAt", Timestamp.from(processingStartedAt))
                .addValue("outboxId", outboxId);

        int updatedCount = namedParameterJdbcTemplate.update(
                """
                UPDATE review_notification_outbox
                SET status = :processingStatus,
                    processing_started_at = :processingStartedAt,
                    processing_attempt = processing_attempt + 1,
                    failed_at = NULL,
                    failure_reason = NULL,
                    failure_type = NULL
                WHERE id = :outboxId
                """,
                updateParameters
        );
        if (updatedCount == 0) {
            return Optional.empty();
        }

        return Optional.of(outboxId);
    }

    private String buildClaimNextIdSelectSql(Collection<Long> excludedOutboxIds) {
        StringBuilder sql = new StringBuilder(
                """
                SELECT id
                FROM review_notification_outbox
                WHERE status IN (:claimableStatuses)
                """
        );

        appendExcludedOutboxIdsClause(sql, excludedOutboxIds);
        sql.append(
                """
                ORDER BY id ASC
                LIMIT 1
                FOR UPDATE SKIP LOCKED
                """
        );

        return sql.toString();
    }

    private void appendExcludedOutboxIdsClause(StringBuilder sql, Collection<Long> excludedOutboxIds) {
        if (excludedOutboxIds == null || excludedOutboxIds.isEmpty()) {
            return;
        }

        sql.append("\n  AND id NOT IN (:excludedOutboxIds)");
    }

    private void addExcludedOutboxIds(
            MapSqlParameterSource parameters,
            Collection<Long> excludedOutboxIds
    ) {
        if (excludedOutboxIds == null || excludedOutboxIds.isEmpty()) {
            return;
        }

        parameters.addValue("excludedOutboxIds", excludedOutboxIds);
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

        BooleanExpression timeoutCondition = reviewNotificationOutbox.processingStartedAt.isNull()
                                                                                          .or(reviewNotificationOutbox.processingStartedAt.lt(
                                                                                                  processingStartedBefore
                                                                                          ));

        long exhaustedCount = queryFactory.update(reviewNotificationOutbox)
                                          .set(reviewNotificationOutbox.status, ReviewNotificationOutboxStatus.FAILED)
                                          .set(
                                                  reviewNotificationOutbox.processingStartedAt,
                                                  Expressions.nullExpression(Instant.class)
                                          )
                                          .set(reviewNotificationOutbox.failedAt, failedAt)
                                          .set(reviewNotificationOutbox.failureReason, failureReason)
                                          .set(
                                                  reviewNotificationOutbox.failureType,
                                                  SlackInteractionFailureType.RETRY_EXHAUSTED
                                          )
                                          .where(
                                                  reviewNotificationOutbox.status.eq(ReviewNotificationOutboxStatus.PROCESSING),
                                                  timeoutCondition,
                                                  reviewNotificationOutbox.processingAttempt.goe(maxAttempts)
                                          )
                                          .execute();

        long recoveredCount = queryFactory.update(reviewNotificationOutbox)
                                          .set(reviewNotificationOutbox.status, ReviewNotificationOutboxStatus.RETRY_PENDING)
                                          .set(
                                                  reviewNotificationOutbox.processingStartedAt,
                                                  Expressions.nullExpression(Instant.class)
                                          )
                                          .set(reviewNotificationOutbox.failedAt, failedAt)
                                          .set(reviewNotificationOutbox.failureReason, failureReason)
                                          .set(
                                                  reviewNotificationOutbox.failureType,
                                                  Expressions.nullExpression(SlackInteractionFailureType.class)
                                          )
                                          .where(
                                                  reviewNotificationOutbox.status.eq(ReviewNotificationOutboxStatus.PROCESSING),
                                                  timeoutCondition,
                                                  reviewNotificationOutbox.processingAttempt.lt(maxAttempts)
                                          )
                                          .execute();

        return Math.toIntExact(exhaustedCount + recoveredCount);
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

    protected String buildEnqueueSql() {
        return """
                INSERT INTO review_notification_outbox (
                    created_at,
                    updated_at,
                    idempotency_key,
                    project_id,
                    team_id,
                    channel_id,
                    payload_json,
                    blocks_json,
                    attachments_json,
                    fallback_text,
                    status,
                    processing_attempt
                )
                VALUES (
                    CURRENT_TIMESTAMP(6),
                    CURRENT_TIMESTAMP(6),
                    :idempotencyKey,
                    :projectId,
                    :teamId,
                    :channelId,
                    :payloadJson,
                    :blocksJson,
                    :attachmentsJson,
                    :fallbackText,
                    :pendingStatus,
                    :processingAttempt
                )
                ON DUPLICATE KEY UPDATE
                    idempotency_key = idempotency_key
                """;
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

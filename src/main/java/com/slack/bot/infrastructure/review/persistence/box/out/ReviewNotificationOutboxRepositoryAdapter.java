package com.slack.bot.infrastructure.review.persistence.box.out;

import static com.slack.bot.infrastructure.review.box.out.QReviewNotificationOutbox.reviewNotificationOutbox;

import com.querydsl.core.Tuple;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.slack.bot.infrastructure.common.FailureSnapshotDefaults;
import com.slack.bot.infrastructure.interaction.box.SlackInteractionFailureType;
import com.slack.bot.infrastructure.review.box.out.ReviewNotificationOutbox;
import com.slack.bot.infrastructure.review.box.out.ReviewNotificationOutboxHistory;
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
    private final JpaReviewNotificationOutboxHistoryRepository historyRepository;

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
                .addValue("processingAttempt", outbox.getProcessingAttempt())
                .addValue("noProcessingStartedAt", Timestamp.from(FailureSnapshotDefaults.NO_PROCESSING_STARTED_AT))
                .addValue("noSentAt", Timestamp.from(FailureSnapshotDefaults.NO_SENT_AT))
                .addValue("noFailureAt", Timestamp.from(FailureSnapshotDefaults.NO_FAILURE_AT))
                .addValue("noFailureReason", FailureSnapshotDefaults.NO_FAILURE_REASON)
                .addValue("noneFailureType", SlackInteractionFailureType.NONE.name());

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
    @Transactional
    public boolean renewProcessingLease(
            Long outboxId,
            Instant currentProcessingStartedAt,
            Instant renewedProcessingStartedAt
    ) {
        validateRenewProcessingLeaseArguments(
                outboxId,
                currentProcessingStartedAt,
                renewedProcessingStartedAt
        );

        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("renewedProcessingStartedAt", Timestamp.from(renewedProcessingStartedAt))
                .addValue("outboxId", outboxId)
                .addValue("processingStatus", ReviewNotificationOutboxStatus.PROCESSING.name())
                .addValue("currentProcessingStartedAt", Timestamp.from(currentProcessingStartedAt));

        int updatedCount = namedParameterJdbcTemplate.update(
                """
                UPDATE review_notification_outbox
                SET updated_at = CURRENT_TIMESTAMP(6),
                    processing_started_at = :renewedProcessingStartedAt
                WHERE id = :outboxId
                  AND status = :processingStatus
                  AND processing_started_at = :currentProcessingStartedAt
                """,
                parameters
        );

        return updatedCount > 0;
    }

    @Override
    @Transactional
    public boolean saveIfProcessingLeaseMatched(
            ReviewNotificationOutbox outbox,
            Instant claimedProcessingStartedAt
    ) {
        return saveIfProcessingLeaseMatched(outbox, null, claimedProcessingStartedAt);
    }

    @Override
    @Transactional
    public boolean saveIfProcessingLeaseMatched(
            ReviewNotificationOutbox outbox,
            ReviewNotificationOutboxHistory history,
            Instant claimedProcessingStartedAt
    ) {
        validateSaveIfProcessingLeaseMatchedArguments(outbox, claimedProcessingStartedAt);

        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("status", outbox.getStatus().name())
                .addValue("processingStartedAt", toTimestamp(outbox.getProcessingStartedAt()))
                .addValue("sentAt", toTimestamp(outbox.getSentAt()))
                .addValue("failedAt", toTimestamp(outbox.getFailedAt()))
                .addValue("failureReason", outbox.getFailureReason())
                .addValue("failureType", resolveFailureTypeName(outbox))
                .addValue("outboxId", outbox.getId())
                .addValue("processingStatus", ReviewNotificationOutboxStatus.PROCESSING.name())
                .addValue("claimedProcessingStartedAt", Timestamp.from(claimedProcessingStartedAt));

        int updatedCount = namedParameterJdbcTemplate.update(
                """
                UPDATE review_notification_outbox
                SET updated_at = CURRENT_TIMESTAMP(6),
                    status = :status,
                    processing_started_at = :processingStartedAt,
                    sent_at = :sentAt,
                    failed_at = :failedAt,
                    failure_reason = :failureReason,
                    failure_type = :failureType
                WHERE id = :outboxId
                  AND status = :processingStatus
                  AND processing_started_at = :claimedProcessingStartedAt
                """,
                parameters
        );

        if (updatedCount == 0) {
            return false;
        }

        if (history != null) {
            historyRepository.save(history.bindOutboxId(outbox.getId()));
        }

        return true;
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
                    sent_at = :noSentAt,
                    failed_at = :noFailureAt,
                    failure_reason = :noFailureReason,
                    failure_type = :noneFailureType
                WHERE id = :outboxId
                """,
                updateParameters
                        .addValue("noSentAt", Timestamp.from(FailureSnapshotDefaults.NO_SENT_AT))
                        .addValue("noFailureAt", Timestamp.from(FailureSnapshotDefaults.NO_FAILURE_AT))
                        .addValue("noFailureReason", FailureSnapshotDefaults.NO_FAILURE_REASON)
                        .addValue("noneFailureType", SlackInteractionFailureType.NONE.name())
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

        BooleanExpression timeoutCondition = reviewNotificationOutbox.processingStartedAt.lt(processingStartedBefore);

        int exhaustedCount = recoverTimeoutProcessingByStatus(
                timeoutCondition,
                reviewNotificationOutbox.processingAttempt.goe(maxAttempts),
                ReviewNotificationOutboxStatus.FAILED,
                failedAt,
                failureReason,
                SlackInteractionFailureType.RETRY_EXHAUSTED
        );
        int recoveredCount = recoverTimeoutProcessingByStatus(
                timeoutCondition,
                reviewNotificationOutbox.processingAttempt.lt(maxAttempts),
                ReviewNotificationOutboxStatus.RETRY_PENDING,
                failedAt,
                failureReason,
                SlackInteractionFailureType.NONE
        );

        return exhaustedCount + recoveredCount;
    }

    private int recoverTimeoutProcessingByStatus(
            BooleanExpression timeoutCondition,
            BooleanExpression attemptCondition,
            ReviewNotificationOutboxStatus targetStatus,
            Instant failedAt,
            String failureReason,
            SlackInteractionFailureType failureType
    ) {
        List<Tuple> timedOutRows = queryFactory
                .select(reviewNotificationOutbox.id, reviewNotificationOutbox.processingAttempt)
                .from(reviewNotificationOutbox)
                .where(
                        reviewNotificationOutbox.status.eq(ReviewNotificationOutboxStatus.PROCESSING),
                        timeoutCondition,
                        attemptCondition
                )
                .fetch();

        int recoveredCount = 0;
        for (Tuple row : timedOutRows) {
            Long outboxId = row.get(reviewNotificationOutbox.id);
            Integer processingAttempt = row.get(reviewNotificationOutbox.processingAttempt);
            long updatedCount = queryFactory
                    .update(reviewNotificationOutbox)
                    .set(reviewNotificationOutbox.status, targetStatus)
                    .set(
                            reviewNotificationOutbox.processingStartedAt,
                            FailureSnapshotDefaults.NO_PROCESSING_STARTED_AT
                    )
                    .set(reviewNotificationOutbox.sentAt, FailureSnapshotDefaults.NO_SENT_AT)
                    .set(reviewNotificationOutbox.failedAt, failedAt)
                    .set(reviewNotificationOutbox.failureReason, failureReason)
                    .set(reviewNotificationOutbox.failureType, failureType)
                    .where(
                            reviewNotificationOutbox.id.eq(outboxId),
                            reviewNotificationOutbox.status.eq(ReviewNotificationOutboxStatus.PROCESSING),
                            timeoutCondition,
                            attemptCondition
                    )
                    .execute();
            if (updatedCount == 0) {
                continue;
            }

            historyRepository.save(
                    ReviewNotificationOutboxHistory.completed(
                            outboxId,
                            processingAttempt,
                            targetStatus,
                            failedAt,
                            failureReason,
                            failureType
                    )
            );
            recoveredCount++;
        }

        return recoveredCount;
    }

    private void validateProcessingStartedAt(Instant processingStartedAt) {
        if (processingStartedAt == null) {
            throw new IllegalArgumentException("processingStartedAt은 비어 있을 수 없습니다.");
        }
    }

    private void validateSaveIfProcessingLeaseMatchedArguments(
            ReviewNotificationOutbox outbox,
            Instant claimedProcessingStartedAt
    ) {
        if (outbox == null) {
            throw new IllegalArgumentException("outbox는 비어 있을 수 없습니다.");
        }
        if (outbox.getId() == null) {
            throw new IllegalArgumentException("outboxId는 비어 있을 수 없습니다.");
        }
        validateProcessingStartedAt(claimedProcessingStartedAt);
    }

    private void validateRenewProcessingLeaseArguments(
            Long outboxId,
            Instant currentProcessingStartedAt,
            Instant renewedProcessingStartedAt
    ) {
        if (outboxId == null) {
            throw new IllegalArgumentException("outboxId는 비어 있을 수 없습니다.");
        }
        validateProcessingStartedAt(currentProcessingStartedAt);
        validateProcessingStartedAt(renewedProcessingStartedAt);
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
                    processing_attempt,
                    processing_started_at,
                    sent_at,
                    failed_at,
                    failure_reason,
                    failure_type
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
                    :processingAttempt,
                    :noProcessingStartedAt,
                    :noSentAt,
                    :noFailureAt,
                    :noFailureReason,
                    :noneFailureType
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

    private Timestamp toTimestamp(Instant instant) {
        return Timestamp.from(instant);
    }

    private String resolveFailureTypeName(ReviewNotificationOutbox outbox) {
        return outbox.getFailureType().name();
    }
}

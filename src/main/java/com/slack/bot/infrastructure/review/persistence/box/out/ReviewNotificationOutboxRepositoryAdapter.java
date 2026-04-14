package com.slack.bot.infrastructure.review.persistence.box.out;

import com.slack.bot.infrastructure.common.BoxEventTime;
import com.slack.bot.infrastructure.interaction.box.SlackInteractionFailureType;
import com.slack.bot.infrastructure.review.box.out.ReviewNotificationOutbox;
import com.slack.bot.infrastructure.review.box.out.ReviewNotificationOutboxHistory;
import com.slack.bot.infrastructure.review.box.out.ReviewNotificationOutboxStatus;
import com.slack.bot.infrastructure.review.box.out.repository.ReviewNotificationOutboxRepository;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
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

    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;
    private final JpaReviewNotificationOutboxRepository repository;
    private final JpaReviewNotificationOutboxHistoryRepository historyRepository;

    @Override
    public boolean enqueue(ReviewNotificationOutbox outbox) {
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("messageType", outbox.getMessageType().name())
                .addValue("idempotencyKey", outbox.getIdempotencyKey())
                .addValue("teamId", outbox.getTeamId())
                .addValue("channelId", outbox.getChannelId())
                .addValue("payloadJsonState", outbox.getPayloadJson().getState().name())
                .addValue("payloadJson", outbox.getPayloadJson().valueOrBlank())
                .addValue("blocksJsonState", outbox.getBlocksJson().getState().name())
                .addValue("blocksJson", outbox.getBlocksJson().valueOrBlank())
                .addValue("attachmentsJsonState", outbox.getAttachmentsJson().getState().name())
                .addValue("attachmentsJson", outbox.getAttachmentsJson().valueOrBlank())
                .addValue("fallbackTextState", outbox.getFallbackText().getState().name())
                .addValue("fallbackText", outbox.getFallbackText().valueOrBlank())
                .addValue("pendingStatus", outbox.getStatus().name())
                .addValue("processingAttempt", outbox.getProcessingAttempt());
        addProjectId(parameters, outbox);
        addAbsentCompletionState(parameters);

        int updatedCount = namedParameterJdbcTemplate.update(
                buildEnqueueSql(),
                parameters
        );

        return updatedCount > 0;
    }

    @Override
    @Transactional
    public ReviewNotificationOutbox save(ReviewNotificationOutbox outbox) {
        ReviewNotificationOutboxJpaEntity entity = findOutboxEntity(outbox);
        entity.apply(outbox);
        return repository.save(entity).toDomain();
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
        return saveIfProcessingLeaseMatched(
                outbox,
                ReviewNotificationOutboxHistoryHolder.absent(),
                claimedProcessingStartedAt
        );
    }

    @Override
    @Transactional
    public boolean saveIfProcessingLeaseMatched(
            ReviewNotificationOutbox outbox,
            ReviewNotificationOutboxHistory history,
            Instant claimedProcessingStartedAt
    ) {
        return saveIfProcessingLeaseMatched(
                outbox,
                ReviewNotificationOutboxHistoryHolder.of(history),
                claimedProcessingStartedAt
        );
    }

    private boolean saveIfProcessingLeaseMatched(
            ReviewNotificationOutbox outbox,
            ReviewNotificationOutboxHistoryHolder historyHolder,
            Instant claimedProcessingStartedAt
    ) {
        validateSaveIfProcessingLeaseMatchedArguments(outbox, claimedProcessingStartedAt);

        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("status", outbox.getStatus().name())
                .addValue("outboxId", outbox.getId())
                .addValue("processingStatus", ReviewNotificationOutboxStatus.PROCESSING.name())
                .addValue("claimedProcessingStartedAt", Timestamp.from(claimedProcessingStartedAt));
        addProcessingLease(parameters, outbox);
        addEventTime(parameters, "sentAt", outbox.getSentTime());
        addEventTime(parameters, "failedAt", outbox.getFailedTime());
        addFailureReason(parameters, outbox);
        addFailureType(parameters, outbox);

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

        if (historyHolder.isPresent()) {
            saveHistory(historyHolder.value());
        }

        return true;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ReviewNotificationOutbox> findById(Long outboxId) {
        return repository.findDomainById(outboxId);
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
                    sent_at = :sentAt,
                    failed_at = :failedAt,
                    failure_reason = :failureReason,
                    failure_type = :failureType
                WHERE id = :outboxId
                """,
                updateParameters
                        .addValue("sentAt", null)
                        .addValue("failedAt", null)
                        .addValue("failureReason", null)
                        .addValue("failureType", null)
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
            int maxAttempts,
            int recoveryBatchSize
    ) {
        validateProcessingStartedBefore(processingStartedBefore);
        validateFailedAt(failedAt);
        validateFailureReason(failureReason);
        validateMaxAttempts(maxAttempts);
        validateRecoveryBatchSize(recoveryBatchSize);

        List<TimeoutRecoveryTarget> timedOutRows = selectTimeoutRecoveryTargets(
                processingStartedBefore,
                recoveryBatchSize
        );

        if (timedOutRows.isEmpty()) {
            return 0;
        }

        List<Long> outboxIds = new ArrayList<>();
        for (TimeoutRecoveryTarget row : timedOutRows) {
            outboxIds.add(row.outboxId());
        }

        LocalDateTime recoveryUpdatedAt = LocalDateTime.ofInstant(failedAt, ZoneOffset.UTC);
        long updatedCount = namedParameterJdbcTemplate.update(
                """
                UPDATE review_notification_outbox
                SET status = CASE
                        WHEN processing_attempt >= :maxAttempts THEN :failedStatus
                        ELSE :retryPendingStatus
                    END,
                    updated_at = :recoveryUpdatedAt,
                    processing_started_at = :processingStartedAt,
                    sent_at = :sentAt,
                    failed_at = :failedAt,
                    failure_reason = :failureReason,
                    failure_type = CASE
                        WHEN processing_attempt >= :maxAttempts THEN :retryExhaustedFailureType
                        ELSE :processingTimeoutFailureType
                    END
                WHERE id IN (:outboxIds)
                  AND status = :processingStatus
                  AND processing_started_at < :processingStartedBefore
                """,
                new MapSqlParameterSource()
                        .addValue("maxAttempts", maxAttempts)
                        .addValue("failedStatus", ReviewNotificationOutboxStatus.FAILED.name())
                        .addValue("retryPendingStatus", ReviewNotificationOutboxStatus.RETRY_PENDING.name())
                        .addValue("recoveryUpdatedAt", Timestamp.valueOf(recoveryUpdatedAt))
                        .addValue("processingStartedAt", null)
                        .addValue("sentAt", null)
                        .addValue("failedAt", Timestamp.from(failedAt))
                        .addValue("failureReason", failureReason)
                        .addValue("retryExhaustedFailureType", SlackInteractionFailureType.RETRY_EXHAUSTED.name())
                        .addValue("processingTimeoutFailureType", SlackInteractionFailureType.PROCESSING_TIMEOUT.name())
                        .addValue("outboxIds", outboxIds)
                        .addValue("processingStatus", ReviewNotificationOutboxStatus.PROCESSING.name())
                        .addValue("processingStartedBefore", Timestamp.from(processingStartedBefore))
        );
        if (updatedCount == 0) {
            return 0;
        }

        batchInsertTimeoutRecoveryHistory(
                timedOutRows,
                failedAt,
                failureReason,
                maxAttempts
        );

        return Math.toIntExact(updatedCount);
    }

    @Override
    @Transactional
    public int deleteCompletedBefore(Instant completedBefore, int deleteBatchSize) {
        validateCompletedBefore(completedBefore);
        validateDeleteBatchSize(deleteBatchSize);

        List<Long> deletableOutboxIds = selectCompletedDeletionTargetIds(completedBefore, deleteBatchSize);
        if (deletableOutboxIds.isEmpty()) {
            return 0;
        }

        deleteHistories(deletableOutboxIds);
        return deleteOutboxes(deletableOutboxIds);
    }

    private void batchInsertTimeoutRecoveryHistory(
            List<TimeoutRecoveryTarget> timedOutRows,
            Instant failedAt,
            String failureReason,
            int maxAttempts
    ) {
        List<MapSqlParameterSource> batchParameters = new ArrayList<>();

        for (TimeoutRecoveryTarget row : timedOutRows) {
            ReviewNotificationOutboxStatus targetStatus = resolveTimeoutRecoveryStatus(
                    row.processingAttempt(),
                    maxAttempts
            );
            SlackInteractionFailureType failureType = resolveTimeoutRecoveryFailureType(
                    row.processingAttempt(),
                    maxAttempts
            );
            batchParameters.add(new MapSqlParameterSource()
                    .addValue("createdAt", Timestamp.from(failedAt))
                    .addValue("updatedAt", Timestamp.from(failedAt))
                    .addValue("outboxId", row.outboxId())
                    .addValue("processingAttempt", row.processingAttempt())
                    .addValue("status", targetStatus.name())
                    .addValue("completedAt", Timestamp.from(failedAt))
                    .addValue("failureReason", failureReason)
                    .addValue("failureType", failureType.name()));
        }

        namedParameterJdbcTemplate.batchUpdate(
                """
                INSERT INTO review_notification_outbox_history (
                    created_at,
                    updated_at,
                    outbox_id,
                    processing_attempt,
                    status,
                    completed_at,
                    failure_reason,
                    failure_type
                )
                VALUES (
                    :createdAt,
                    :updatedAt,
                    :outboxId,
                    :processingAttempt,
                    :status,
                    :completedAt,
                    :failureReason,
                    :failureType
                )
                """,
                batchParameters.toArray(new MapSqlParameterSource[0])
        );
    }

    private List<Long> selectCompletedDeletionTargetIds(
            Instant completedBefore,
            int deleteBatchSize
    ) {
        return namedParameterJdbcTemplate.query(
                buildCompletedDeletionTargetSelectSql(deleteBatchSize),
                new MapSqlParameterSource()
                        .addValue("sentStatus", ReviewNotificationOutboxStatus.SENT.name())
                        .addValue("failedStatus", ReviewNotificationOutboxStatus.FAILED.name())
                        .addValue("completedBefore", Timestamp.from(completedBefore)),
                (resultSet, rowNum) -> resultSet.getLong("id")
        );
    }

    private String buildCompletedDeletionTargetSelectSql(int deleteBatchSize) {
        StringBuilder sql = new StringBuilder(
                """
                SELECT id
                FROM review_notification_outbox
                WHERE (status = :sentStatus AND sent_at < :completedBefore)
                   OR (status = :failedStatus AND failed_at < :completedBefore)
                ORDER BY COALESCE(sent_at, failed_at) ASC, id ASC
                LIMIT
                """
        );
        sql.append(deleteBatchSize);
        sql.append("\nFOR UPDATE SKIP LOCKED");
        return sql.toString();
    }

    private void deleteHistories(List<Long> deletableOutboxIds) {
        namedParameterJdbcTemplate.update(
                """
                DELETE FROM review_notification_outbox_history
                WHERE outbox_id IN (:outboxIds)
                """,
                new MapSqlParameterSource()
                        .addValue("outboxIds", deletableOutboxIds)
        );
    }

    private int deleteOutboxes(List<Long> deletableOutboxIds) {
        return namedParameterJdbcTemplate.update(
                """
                DELETE FROM review_notification_outbox
                WHERE id IN (:outboxIds)
                """,
                new MapSqlParameterSource()
                        .addValue("outboxIds", deletableOutboxIds)
        );
    }

    private List<TimeoutRecoveryTarget> selectTimeoutRecoveryTargets(
            Instant processingStartedBefore,
            int recoveryBatchSize
    ) {
        return namedParameterJdbcTemplate.query(
                buildTimeoutRecoverySelectSql(recoveryBatchSize),
                new MapSqlParameterSource()
                        .addValue("processingStatus", ReviewNotificationOutboxStatus.PROCESSING.name())
                        .addValue("processingStartedBefore", Timestamp.from(processingStartedBefore)),
                (resultSet, rowNum) -> new TimeoutRecoveryTarget(
                        resultSet.getLong("id"),
                        resultSet.getInt("processing_attempt")
                )
        );
    }

    private String buildTimeoutRecoverySelectSql(int recoveryBatchSize) {
        StringBuilder sql = new StringBuilder(
                """
                SELECT id, processing_attempt
                FROM review_notification_outbox
                WHERE status = :processingStatus
                  AND processing_started_at < :processingStartedBefore
                ORDER BY processing_started_at ASC, id ASC
                LIMIT
                """
        );
        sql.append(recoveryBatchSize);
        sql.append("\nFOR UPDATE SKIP LOCKED");
        return sql.toString();
    }

    private ReviewNotificationOutboxStatus resolveTimeoutRecoveryStatus(int processingAttempt, int maxAttempts) {
        if (processingAttempt >= maxAttempts) {
            return ReviewNotificationOutboxStatus.FAILED;
        }

        return ReviewNotificationOutboxStatus.RETRY_PENDING;
    }

    private SlackInteractionFailureType resolveTimeoutRecoveryFailureType(int processingAttempt, int maxAttempts) {
        if (processingAttempt >= maxAttempts) {
            return SlackInteractionFailureType.RETRY_EXHAUSTED;
        }

        return SlackInteractionFailureType.PROCESSING_TIMEOUT;
    }

    private record TimeoutRecoveryTarget(Long outboxId, Integer processingAttempt) {
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

    private void validateCompletedBefore(Instant completedBefore) {
        if (completedBefore == null) {
            throw new IllegalArgumentException("completedBefore는 비어 있을 수 없습니다.");
        }
    }

    protected String buildEnqueueSql() {
        return """
                INSERT INTO review_notification_outbox (
                    created_at,
                    updated_at,
                    message_type,
                    idempotency_key,
                    project_id,
                    team_id,
                    channel_id,
                    payload_json_state,
                    payload_json,
                    blocks_json_state,
                    blocks_json,
                    attachments_json_state,
                    attachments_json,
                    fallback_text_state,
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
                    :messageType,
                    :idempotencyKey,
                    :projectId,
                    :teamId,
                    :channelId,
                    :payloadJsonState,
                    :payloadJson,
                    :blocksJsonState,
                    :blocksJson,
                    :attachmentsJsonState,
                    :attachmentsJson,
                    :fallbackTextState,
                    :fallbackText,
                    :pendingStatus,
                    :processingAttempt,
                    :processingStartedAt,
                    :sentAt,
                    :failedAt,
                    :failureReason,
                    :failureType
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

    private void validateRecoveryBatchSize(int recoveryBatchSize) {
        if (recoveryBatchSize <= 0) {
            throw new IllegalArgumentException("recoveryBatchSize는 0보다 커야 합니다.");
        }
    }

    private void validateDeleteBatchSize(int deleteBatchSize) {
        if (deleteBatchSize <= 0) {
            throw new IllegalArgumentException("deleteBatchSize는 0보다 커야 합니다.");
        }
    }

    private ReviewNotificationOutboxJpaEntity findOutboxEntity(ReviewNotificationOutbox outbox) {
        if (!outbox.hasId()) {
            return new ReviewNotificationOutboxJpaEntity();
        }

        return repository.findById(outbox.getId())
                .orElseThrow(() -> new IllegalStateException("저장 대상 outbox를 찾을 수 없습니다. id=" + outbox.getId()));
    }

    private void saveHistory(ReviewNotificationOutboxHistory history) {
        ReviewNotificationOutboxHistoryJpaEntity entity = new ReviewNotificationOutboxHistoryJpaEntity();
        entity.apply(history);
        historyRepository.save(entity);
    }

    private void addProjectId(MapSqlParameterSource parameters, ReviewNotificationOutbox outbox) {
        if (!outbox.getProjectId().isPresent()) {
            parameters.addValue("projectId", null);
            return;
        }

        parameters.addValue("projectId", outbox.getProjectId().value());
    }

    private void addAbsentCompletionState(MapSqlParameterSource parameters) {
        parameters.addValue("processingStartedAt", null);
        parameters.addValue("sentAt", null);
        parameters.addValue("failedAt", null);
        parameters.addValue("failureReason", null);
        parameters.addValue("failureType", null);
    }

    private void addProcessingLease(
            MapSqlParameterSource parameters,
            ReviewNotificationOutbox outbox
    ) {
        if (!outbox.getProcessingLease().isClaimed()) {
            parameters.addValue("processingStartedAt", null);
            return;
        }

        parameters.addValue("processingStartedAt", Timestamp.from(outbox.getProcessingLease().startedAt()));
    }

    private void addEventTime(
            MapSqlParameterSource parameters,
            String parameterName,
            BoxEventTime eventTime
    ) {
        if (!eventTime.isPresent()) {
            parameters.addValue(parameterName, null);
            return;
        }

        parameters.addValue(parameterName, Timestamp.from(eventTime.occurredAt()));
    }

    private void addFailureReason(
            MapSqlParameterSource parameters,
            ReviewNotificationOutbox outbox
    ) {
        if (!outbox.getFailure().isPresent()) {
            parameters.addValue("failureReason", null);
            return;
        }

        parameters.addValue("failureReason", outbox.getFailure().reason());
    }

    private void addFailureType(
            MapSqlParameterSource parameters,
            ReviewNotificationOutbox outbox
    ) {
        if (!outbox.getFailure().isPresent()) {
            parameters.addValue("failureType", null);
            return;
        }

        parameters.addValue("failureType", outbox.getFailure().type().name());
    }

    private sealed interface ReviewNotificationOutboxHistoryHolder
            permits AbsentReviewNotificationOutboxHistoryHolder, PresentReviewNotificationOutboxHistoryHolder {

        static ReviewNotificationOutboxHistoryHolder absent() {
            return AbsentReviewNotificationOutboxHistoryHolder.INSTANCE;
        }

        static ReviewNotificationOutboxHistoryHolder of(ReviewNotificationOutboxHistory history) {
            if (history == null) {
                return absent();
            }

            return new PresentReviewNotificationOutboxHistoryHolder(history);
        }

        boolean isPresent();

        default ReviewNotificationOutboxHistory value() {
            throw new IllegalStateException("history가 없는 상태입니다.");
        }
    }

    private static final class AbsentReviewNotificationOutboxHistoryHolder
            implements ReviewNotificationOutboxHistoryHolder {

        private static final AbsentReviewNotificationOutboxHistoryHolder INSTANCE =
                new AbsentReviewNotificationOutboxHistoryHolder();

        private AbsentReviewNotificationOutboxHistoryHolder() {
        }

        @Override
        public boolean isPresent() {
            return false;
        }
    }

    private record PresentReviewNotificationOutboxHistoryHolder(
            ReviewNotificationOutboxHistory value
    ) implements ReviewNotificationOutboxHistoryHolder {

        private PresentReviewNotificationOutboxHistoryHolder {
            if (value == null) {
                throw new IllegalArgumentException("history는 비어 있을 수 없습니다.");
            }
        }

        @Override
        public boolean isPresent() {
            return true;
        }
    }
}

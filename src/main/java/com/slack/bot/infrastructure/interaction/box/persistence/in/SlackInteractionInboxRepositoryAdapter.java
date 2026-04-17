package com.slack.bot.infrastructure.interaction.box.persistence.in;

import com.slack.bot.infrastructure.common.BoxProcessingLease;
import com.slack.bot.infrastructure.interaction.box.SlackInteractionFailureType;
import com.slack.bot.infrastructure.interaction.box.in.SlackInteractionInbox;
import com.slack.bot.infrastructure.interaction.box.in.SlackInteractionInboxHistory;
import com.slack.bot.infrastructure.interaction.box.in.SlackInteractionInboxStatus;
import com.slack.bot.infrastructure.interaction.box.in.SlackInteractionInboxType;
import com.slack.bot.infrastructure.interaction.box.in.repository.SlackInteractionInboxRepository;
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
public class SlackInteractionInboxRepositoryAdapter implements SlackInteractionInboxRepository {

    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;
    private final SlackInteractionInboxMybatisMapper slackInteractionInboxMybatisMapper;
    private final SlackInteractionInboxHistoryMybatisMapper slackInteractionInboxHistoryMybatisMapper;

    @Override
    public boolean enqueue(SlackInteractionInboxType interactionType, String idempotencyKey, String payloadJson) {
        SlackInteractionInbox inbox = SlackInteractionInbox.pending(interactionType, idempotencyKey, payloadJson);
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("interactionType", inbox.getInteractionType().name())
                .addValue("idempotencyKey", inbox.getIdempotencyKey())
                .addValue("payloadJson", inbox.getPayloadJson())
                .addValue("pendingStatus", inbox.getStatus().name())
                .addValue("processingAttempt", inbox.getProcessingAttempt())
                .addValue("processingStartedAt", null)
                .addValue("processedAt", null)
                .addValue("failedAt", null)
                .addValue("failureReason", null)
                .addValue("failureType", null);

        int updatedCount = namedParameterJdbcTemplate.update(buildEnqueueSql(), parameters);

        return updatedCount > 0;
    }

    @Override
    @Transactional
    public Optional<Long> claimNextId(
            SlackInteractionInboxType interactionType,
            Instant processingStartedAt,
            Collection<Long> excludedInboxIds
    ) {
        validateInteractionType(interactionType);
        validateProcessingStartedAt(processingStartedAt);

        return slackInteractionInboxMybatisMapper.findClaimableRowForUpdate(
                interactionType.name(),
                List.of(
                        SlackInteractionInboxStatus.PENDING.name(),
                        SlackInteractionInboxStatus.RETRY_PENDING.name()
                ),
                excludedInboxIds
        ).map(row -> {
            SlackInteractionInbox inbox = row.toDomain();
            inbox.claim(processingStartedAt);
            updateInbox(inbox);
            return inbox.getId();
        });
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<SlackInteractionInbox> findById(Long inboxId) {
        return slackInteractionInboxMybatisMapper.findDomainById(inboxId);
    }

    @Override
    @Transactional
    public int recoverTimeoutProcessing(
            SlackInteractionInboxType interactionType,
            Instant processingStartedBefore,
            Instant failedAt,
            String failureReason,
            int maxAttempts,
            int recoveryBatchSize
    ) {
        validateRecoverTimeoutProcessingArguments(
                interactionType,
                processingStartedBefore,
                failedAt,
                failureReason,
                maxAttempts,
                recoveryBatchSize
        );

        List<SlackInteractionInboxRow> timedOutInboxRows = slackInteractionInboxMybatisMapper.findTimeoutRecoveryRowsForUpdate(
                interactionType.name(),
                SlackInteractionInboxStatus.PROCESSING.name(),
                processingStartedBefore,
                recoveryBatchSize
        );

        int recoveredCount = 0;
        for (SlackInteractionInboxRow timedOutInboxRow : timedOutInboxRows) {
            SlackInteractionInbox inbox = timedOutInboxRow.toDomain();
            if (!isTimeoutRecoverable(inbox, interactionType, processingStartedBefore)) {
                continue;
            }

            SlackInteractionInboxHistory history = recoverTimeoutProcessing(
                    inbox,
                    failedAt,
                    failureReason,
                    maxAttempts
            );

            updateInbox(inbox);
            saveHistory(history.bindInboxId(inbox.getId()));
            recoveredCount++;
        }

        return recoveredCount;
    }

    @Override
    @Transactional
    public int deleteCompletedBefore(Instant completedBefore, int deleteBatchSize) {
        validateCompletedBefore(completedBefore);
        validateDeleteBatchSize(deleteBatchSize);

        List<Long> deletableInboxIds = selectCompletedDeletionTargetIds(completedBefore, deleteBatchSize);
        if (deletableInboxIds.isEmpty()) {
            return 0;
        }

        deleteHistories(deletableInboxIds);
        return deleteInboxes(deletableInboxIds);
    }

    private List<Long> selectCompletedDeletionTargetIds(
            Instant completedBefore,
            int deleteBatchSize
    ) {
        return namedParameterJdbcTemplate.query(
                buildCompletedDeletionTargetSelectSql(deleteBatchSize),
                new MapSqlParameterSource()
                        .addValue("processedStatus", SlackInteractionInboxStatus.PROCESSED.name())
                        .addValue("failedStatus", SlackInteractionInboxStatus.FAILED.name())
                        .addValue("completedBefore", Timestamp.from(completedBefore)),
                (resultSet, rowNum) -> resultSet.getLong("id")
        );
    }

    private String buildCompletedDeletionTargetSelectSql(int deleteBatchSize) {
        StringBuilder sql = new StringBuilder(
                """
                SELECT id
                FROM slack_interaction_inbox
                WHERE (status = :processedStatus AND processed_at < :completedBefore)
                   OR (status = :failedStatus AND failed_at < :completedBefore)
                ORDER BY COALESCE(processed_at, failed_at) ASC, id ASC
                LIMIT
                """
        );
        sql.append(deleteBatchSize);
        return sql.toString();
    }

    private void deleteHistories(List<Long> deletableInboxIds) {
        namedParameterJdbcTemplate.update(
                """
                DELETE FROM slack_interaction_inbox_history
                WHERE inbox_id IN (:inboxIds)
                """,
                new MapSqlParameterSource()
                        .addValue("inboxIds", deletableInboxIds)
        );
    }

    private int deleteInboxes(List<Long> deletableInboxIds) {
        return namedParameterJdbcTemplate.update(
                """
                DELETE FROM slack_interaction_inbox
                WHERE id IN (:inboxIds)
                """,
                new MapSqlParameterSource()
                        .addValue("inboxIds", deletableInboxIds)
        );
    }

    private boolean isTimeoutRecoverable(
            SlackInteractionInbox inbox,
            SlackInteractionInboxType interactionType,
            Instant processingStartedBefore
    ) {
        if (inbox.getInteractionType() != interactionType) {
            return false;
        }
        if (inbox.getStatus() != SlackInteractionInboxStatus.PROCESSING) {
            return false;
        }

        BoxProcessingLease processingLease = inbox.getProcessingLease();
        if (!processingLease.isClaimed()) {
            return false;
        }

        return processingLease.startedAt().isBefore(processingStartedBefore);
    }

    private SlackInteractionInboxHistory recoverTimeoutProcessing(
            SlackInteractionInbox inbox,
            Instant failedAt,
            String failureReason,
            int maxAttempts
    ) {
        SlackInteractionFailureType failureType = resolveTimeoutRecoveryFailureType(
                inbox.getProcessingAttempt(),
                maxAttempts
        );
        if (failureType == SlackInteractionFailureType.RETRY_EXHAUSTED) {
            return inbox.markFailed(failedAt, failureReason, failureType);
        }

        return inbox.markRetryPending(failedAt, failureReason, failureType);
    }

    private SlackInteractionFailureType resolveTimeoutRecoveryFailureType(int processingAttempt, int maxAttempts) {
        if (processingAttempt >= maxAttempts) {
            return SlackInteractionFailureType.RETRY_EXHAUSTED;
        }

        return SlackInteractionFailureType.PROCESSING_TIMEOUT;
    }

    private void validateRecoverTimeoutProcessingArguments(
            SlackInteractionInboxType interactionType,
            Instant processingStartedBefore,
            Instant failedAt,
            String failureReason,
            int maxAttempts,
            int recoveryBatchSize
    ) {
        validateInteractionType(interactionType);
        validateProcessingStartedBefore(processingStartedBefore);
        validateFailedAt(failedAt);
        validateFailureReason(failureReason);
        validateMaxAttempts(maxAttempts);
        validateRecoveryBatchSize(recoveryBatchSize);
    }

    protected String buildEnqueueSql() {
        return """
                INSERT INTO slack_interaction_inbox (
                    created_at,
                    updated_at,
                    interaction_type,
                    idempotency_key,
                    payload_json,
                    status,
                    processing_attempt,
                    processing_started_at,
                    processed_at,
                    failed_at,
                    failure_reason,
                    failure_type
                )
                VALUES (
                    CURRENT_TIMESTAMP(6),
                    CURRENT_TIMESTAMP(6),
                    :interactionType,
                    :idempotencyKey,
                    :payloadJson,
                    :pendingStatus,
                    :processingAttempt,
                    :processingStartedAt,
                    :processedAt,
                    :failedAt,
                    :failureReason,
                    :failureType
                )
                ON DUPLICATE KEY UPDATE
                    idempotency_key = idempotency_key
                """;
    }

    private void validateInteractionType(SlackInteractionInboxType interactionType) {
        if (interactionType == null) {
            throw new IllegalArgumentException("interactionType은 비어 있을 수 없습니다.");
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

    private void validateCompletedBefore(Instant completedBefore) {
        if (completedBefore == null) {
            throw new IllegalArgumentException("completedBefore는 비어 있을 수 없습니다.");
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

    @Override
    @Transactional
    public SlackInteractionInbox save(SlackInteractionInbox inbox) {
        if (inbox.getId() == null) {
            return insertInbox(inbox);
        }

        return updateInbox(inbox);
    }

    @Override
    @Transactional
    public boolean saveIfProcessingLeaseMatched(
            SlackInteractionInbox inbox,
            Instant claimedProcessingStartedAt
    ) {
        return saveIfProcessingLeaseMatched(inbox, null, claimedProcessingStartedAt);
    }

    @Override
    @Transactional
    public boolean saveIfProcessingLeaseMatched(
            SlackInteractionInbox inbox,
            SlackInteractionInboxHistory history,
            Instant claimedProcessingStartedAt
    ) {
        validateSaveIfProcessingLeaseMatchedArguments(inbox, claimedProcessingStartedAt);

        SlackInteractionInboxRow row = SlackInteractionInboxRow.from(inbox);

        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("status", row.getStatus().name())
                .addValue("processingStartedAt", toNullableTimestamp(row.getProcessingStartedAt()))
                .addValue("processedAt", toNullableTimestamp(row.getProcessedAt()))
                .addValue("failedAt", toNullableTimestamp(row.getFailedAt()))
                .addValue("failureReason", row.getFailureReason())
                .addValue("failureType", toNullableFailureTypeName(row))
                .addValue("inboxId", inbox.getId())
                .addValue("processingStatus", SlackInteractionInboxStatus.PROCESSING.name())
                .addValue("claimedProcessingStartedAt", Timestamp.from(claimedProcessingStartedAt));

        int updatedCount = namedParameterJdbcTemplate.update(
                """
                UPDATE slack_interaction_inbox
                SET updated_at = CURRENT_TIMESTAMP(6),
                    status = :status,
                    processing_started_at = :processingStartedAt,
                    processed_at = :processedAt,
                    failed_at = :failedAt,
                    failure_reason = :failureReason,
                    failure_type = :failureType
                WHERE id = :inboxId
                  AND status = :processingStatus
                  AND processing_started_at = :claimedProcessingStartedAt
                """,
                parameters
        );
        if (updatedCount == 0) {
            return false;
        }

        if (history != null) {
            saveHistory(history.bindInboxId(inbox.getId()));
        }

        return true;
    }

    @Override
    @Transactional
    public SlackInteractionInbox save(SlackInteractionInbox inbox, SlackInteractionInboxHistory history) {
        SlackInteractionInbox saved = save(inbox);
        if (history != null) {
            saveHistory(history.bindInboxId(saved.getId()));
        }

        return saved;
    }

    private SlackInteractionInbox insertInbox(SlackInteractionInbox inbox) {
        SlackInteractionInboxRow row = SlackInteractionInboxRow.from(inbox);
        slackInteractionInboxMybatisMapper.insert(row);
        return row.toDomain();
    }

    private SlackInteractionInbox updateInbox(SlackInteractionInbox inbox) {
        SlackInteractionInboxRow row = SlackInteractionInboxRow.from(inbox);
        int updatedCount = slackInteractionInboxMybatisMapper.update(row);
        if (updatedCount == 0) {
            throw new IllegalStateException("저장 대상 inbox를 찾을 수 없습니다. id=" + inbox.getId());
        }

        return inbox;
    }

    private void saveHistory(SlackInteractionInboxHistory history) {
        slackInteractionInboxHistoryMybatisMapper.insert(SlackInteractionInboxHistoryRow.from(history));
    }

    private Timestamp toNullableTimestamp(Instant value) {
        if (value == null) {
            return null;
        }

        return Timestamp.from(value);
    }

    private String toNullableFailureTypeName(SlackInteractionInboxRow row) {
        if (row.getFailureType() == null) {
            return null;
        }

        return row.getFailureType().name();
    }

    private void validateSaveIfProcessingLeaseMatchedArguments(
            SlackInteractionInbox inbox,
            Instant claimedProcessingStartedAt
    ) {
        if (inbox == null) {
            throw new IllegalArgumentException("inbox는 비어 있을 수 없습니다.");
        }
        if (inbox.getId() == null || inbox.getId() <= 0) {
            throw new IllegalArgumentException("inboxId는 비어 있을 수 없습니다.");
        }

        validateProcessingStartedAt(claimedProcessingStartedAt);
    }
}

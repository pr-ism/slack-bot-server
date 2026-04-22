package com.slack.bot.infrastructure.interaction.box.persistence.out;

import com.slack.bot.infrastructure.common.BoxProcessingLease;
import com.slack.bot.infrastructure.interaction.box.SlackInteractionFailureType;
import com.slack.bot.infrastructure.interaction.box.out.SlackNotificationOutbox;
import com.slack.bot.infrastructure.interaction.box.out.SlackNotificationOutboxHistory;
import com.slack.bot.infrastructure.interaction.box.out.SlackNotificationOutboxStatus;
import com.slack.bot.infrastructure.interaction.box.out.repository.SlackNotificationOutboxRepository;
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
public class SlackNotificationOutboxRepositoryAdapter implements SlackNotificationOutboxRepository {

    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;
    private final SlackNotificationOutboxMybatisMapper slackNotificationOutboxMybatisMapper;
    private final SlackNotificationOutboxHistoryMybatisMapper slackNotificationOutboxHistoryMybatisMapper;

    @Override
    public boolean enqueue(SlackNotificationOutbox outbox) {
        SlackNotificationOutboxRow row = SlackNotificationOutboxRow.from(outbox);
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("messageType", row.getMessageType().name())
                .addValue("idempotencyKey", row.getIdempotencyKey())
                .addValue("teamId", row.getTeamId())
                .addValue("channelId", row.getChannelId())
                .addValue("userIdState", row.getUserIdState().name())
                .addValue("userId", row.getUserId())
                .addValue("textState", row.getTextState().name())
                .addValue("text", row.getText())
                .addValue("blocksJsonState", row.getBlocksJsonState().name())
                .addValue("blocksJson", row.getBlocksJson())
                .addValue("fallbackTextState", row.getFallbackTextState().name())
                .addValue("fallbackText", row.getFallbackText())
                .addValue("pendingStatus", row.getStatus().name())
                .addValue("processingAttempt", row.getProcessingAttempt())
                .addValue("processingLeaseState", row.getProcessingLeaseState().name())
                .addValue("sentTimeState", row.getSentTimeState().name())
                .addValue("failedTimeState", row.getFailedTimeState().name())
                .addValue("failureState", row.getFailureState().name());

        int updatedCount = namedParameterJdbcTemplate.update(buildEnqueueSql(), parameters);

        return updatedCount > 0;
    }

    @Override
    @Transactional
    public SlackNotificationOutbox save(SlackNotificationOutbox outbox) {
        if (!outbox.hasId()) {
            return insertOutbox(outbox);
        }

        return updateOutbox(outbox);
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

        return slackNotificationOutboxMybatisMapper.findRowForUpdateById(outboxId)
                                                   .filter(row -> {
                                                       SlackNotificationOutbox outbox = row.toDomain();
                                                       return outbox.getStatus() == SlackNotificationOutboxStatus.PROCESSING
                                                               && hasClaimedLease(outbox, currentProcessingStartedAt);
                                                   })
                                                   .map(row -> {
                                                       SlackNotificationOutbox outbox = row.toDomain();
                                                       outbox.renewProcessingLease(renewedProcessingStartedAt);
                                                       updateOutbox(outbox);
                                                       return true;
                                                   })
                                                   .orElse(false);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<SlackNotificationOutbox> findById(Long outboxId) {
        return slackNotificationOutboxMybatisMapper.findDomainById(outboxId);
    }

    @Override
    @Transactional
    public Optional<Long> claimNextId(Instant processingStartedAt, Collection<Long> excludedOutboxIds) {
        validateProcessingStartedAt(processingStartedAt);

        return slackNotificationOutboxMybatisMapper.findClaimableRowForUpdate(
                List.of(
                        SlackNotificationOutboxStatus.PENDING.name(),
                        SlackNotificationOutboxStatus.RETRY_PENDING.name()
                ),
                excludedOutboxIds
        ).map(row -> {
            SlackNotificationOutbox outbox = row.toDomain();
            outbox.claim(processingStartedAt);
            updateOutbox(outbox);
            return outbox.getId();
        });
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
        validateRecoverTimeoutProcessingArguments(
                processingStartedBefore,
                failedAt,
                failureReason,
                maxAttempts,
                recoveryBatchSize
        );

        List<SlackNotificationOutboxRow> timedOutOutboxRows = slackNotificationOutboxMybatisMapper.findTimeoutRecoveryRowsForUpdate(
                SlackNotificationOutboxStatus.PROCESSING.name(),
                processingStartedBefore,
                recoveryBatchSize
        );

        int recoveredCount = 0;
        for (SlackNotificationOutboxRow timedOutOutboxRow : timedOutOutboxRows) {
            SlackNotificationOutbox outbox = timedOutOutboxRow.toDomain();
            if (!isTimeoutRecoverable(outbox, processingStartedBefore)) {
                continue;
            }

            SlackNotificationOutboxHistory history = recoverTimeoutProcessing(
                    outbox,
                    failedAt,
                    failureReason,
                    maxAttempts
            );

            updateOutbox(outbox);
            saveHistory(history);
            recoveredCount++;
        }

        return recoveredCount;
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

    private List<Long> selectCompletedDeletionTargetIds(
            Instant completedBefore,
            int deleteBatchSize
    ) {
        return namedParameterJdbcTemplate.query(
                buildCompletedDeletionTargetSelectSql(deleteBatchSize),
                new MapSqlParameterSource()
                        .addValue("sentStatus", SlackNotificationOutboxStatus.SENT.name())
                        .addValue("failedStatus", SlackNotificationOutboxStatus.FAILED.name())
                        .addValue("completedBefore", Timestamp.from(completedBefore)),
                (resultSet, rowNum) -> resultSet.getLong("id")
        );
    }

    private String buildCompletedDeletionTargetSelectSql(int deleteBatchSize) {
        StringBuilder sql = new StringBuilder(
                """
                SELECT id
                FROM slack_notification_outbox
                WHERE (status = :sentStatus AND sent_at < :completedBefore)
                   OR (status = :failedStatus AND failed_at < :completedBefore)
                ORDER BY COALESCE(sent_at, failed_at) ASC, id ASC
                LIMIT
                """
        );
        sql.append(deleteBatchSize);
        return sql.toString();
    }

    private void deleteHistories(List<Long> deletableOutboxIds) {
        namedParameterJdbcTemplate.update(
                """
                DELETE FROM slack_notification_outbox_history
                WHERE outbox_id IN (:outboxIds)
                """,
                new MapSqlParameterSource()
                        .addValue("outboxIds", deletableOutboxIds)
        );
    }

    private int deleteOutboxes(List<Long> deletableOutboxIds) {
        return namedParameterJdbcTemplate.update(
                """
                DELETE FROM slack_notification_outbox
                WHERE id IN (:outboxIds)
                """,
                new MapSqlParameterSource()
                        .addValue("outboxIds", deletableOutboxIds)
        );
    }

    private boolean isTimeoutRecoverable(
            SlackNotificationOutbox outbox,
            Instant processingStartedBefore
    ) {
        if (outbox.getStatus() != SlackNotificationOutboxStatus.PROCESSING) {
            return false;
        }

        BoxProcessingLease processingLease = outbox.getProcessingLease();
        if (!processingLease.isClaimed()) {
            return false;
        }

        return processingLease.startedAt().isBefore(processingStartedBefore);
    }

    private void validateCompletedBefore(Instant completedBefore) {
        if (completedBefore == null) {
            throw new IllegalArgumentException("completedBefore는 비어 있을 수 없습니다.");
        }
    }

    private void validateDeleteBatchSize(int deleteBatchSize) {
        if (deleteBatchSize <= 0) {
            throw new IllegalArgumentException("deleteBatchSize는 0보다 커야 합니다.");
        }
    }

    private SlackNotificationOutboxHistory recoverTimeoutProcessing(
            SlackNotificationOutbox outbox,
            Instant failedAt,
            String failureReason,
            int maxAttempts
    ) {
        SlackInteractionFailureType failureType = resolveTimeoutRecoveryFailureType(
                outbox.getProcessingAttempt(),
                maxAttempts
        );
        if (failureType == SlackInteractionFailureType.RETRY_EXHAUSTED) {
            return outbox.markFailed(failedAt, failureReason, failureType);
        }

        return outbox.markRetryPending(failedAt, failureReason, failureType);
    }

    private SlackInteractionFailureType resolveTimeoutRecoveryFailureType(int processingAttempt, int maxAttempts) {
        if (processingAttempt >= maxAttempts) {
            return SlackInteractionFailureType.RETRY_EXHAUSTED;
        }

        return SlackInteractionFailureType.PROCESSING_TIMEOUT;
    }

    @Override
    @Transactional
    public boolean saveIfProcessingLeaseMatched(
            SlackNotificationOutbox outbox,
            Instant claimedProcessingStartedAt
    ) {
        return saveIfProcessingLeaseMatched(outbox, null, claimedProcessingStartedAt);
    }

    @Override
    @Transactional
    public boolean saveIfProcessingLeaseMatched(
            SlackNotificationOutbox outbox,
            SlackNotificationOutboxHistory history,
            Instant claimedProcessingStartedAt
    ) {
        validateSaveIfProcessingLeaseMatchedArguments(outbox, claimedProcessingStartedAt);
        return slackNotificationOutboxMybatisMapper.findRowForUpdateById(outbox.getId())
                                                   .filter(row -> {
                                                       SlackNotificationOutbox persistedOutbox = row.toDomain();
                                                       return persistedOutbox.getStatus() == SlackNotificationOutboxStatus.PROCESSING
                                                               && hasClaimedLease(persistedOutbox, claimedProcessingStartedAt);
                                                   })
                                                   .map(row -> {
                                                       updateOutbox(outbox);
                                                       if (history != null) {
                                                           saveHistory(history);
                                                       }
                                                       return true;
                                                   })
                                                   .orElse(false);
    }

    protected String buildEnqueueSql() {
        return """
                INSERT INTO slack_notification_outbox (
                    created_at,
                    updated_at,
                    message_type,
                    idempotency_key,
                    team_id,
                    channel_id,
                    user_id_state,
                    user_id,
                    text_state,
                    text,
                    blocks_json_state,
                    blocks_json,
                    fallback_text_state,
                    fallback_text,
                    status,
                    processing_attempt,
                    processing_lease_state,
                    sent_time_state,
                    failed_time_state,
                    failure_state
                )
                VALUES (
                    CURRENT_TIMESTAMP(6),
                    CURRENT_TIMESTAMP(6),
                    :messageType,
                    :idempotencyKey,
                    :teamId,
                    :channelId,
                    :userIdState,
                    :userId,
                    :textState,
                    :text,
                    :blocksJsonState,
                    :blocksJson,
                    :fallbackTextState,
                    :fallbackText,
                    :pendingStatus,
                    :processingAttempt,
                    :processingLeaseState,
                    :sentTimeState,
                    :failedTimeState,
                    :failureState
                )
                ON DUPLICATE KEY UPDATE
                    idempotency_key = idempotency_key
                """;
    }

    private SlackNotificationOutbox insertOutbox(SlackNotificationOutbox outbox) {
        SlackNotificationOutboxRow row = SlackNotificationOutboxRow.from(outbox);
        slackNotificationOutboxMybatisMapper.insert(row);
        return row.toDomain();
    }

    private SlackNotificationOutbox updateOutbox(SlackNotificationOutbox outbox) {
        SlackNotificationOutboxRow row = SlackNotificationOutboxRow.from(outbox);
        int updatedCount = slackNotificationOutboxMybatisMapper.update(row);
        if (updatedCount == 0) {
            throw new IllegalStateException("저장 대상 outbox를 찾을 수 없습니다. id=" + outbox.getId());
        }

        return outbox;
    }

    private void saveHistory(SlackNotificationOutboxHistory history) {
        slackNotificationOutboxHistoryMybatisMapper.insert(SlackNotificationOutboxHistoryRow.from(history));
    }

    private void validateRecoverTimeoutProcessingArguments(
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
    }

    private void validateProcessingStartedAt(Instant processingStartedAt) {
        if (processingStartedAt == null) {
            throw new IllegalArgumentException("processingStartedAt은 비어 있을 수 없습니다.");
        }
    }

    private void validateSaveIfProcessingLeaseMatchedArguments(
            SlackNotificationOutbox outbox,
            Instant claimedProcessingStartedAt
    ) {
        if (outbox == null) {
            throw new IllegalArgumentException("outbox는 비어 있을 수 없습니다.");
        }
        if (!outbox.hasId()) {
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

    private boolean hasClaimedLease(SlackNotificationOutbox outbox, Instant claimedProcessingStartedAt) {
        BoxProcessingLease processingLease = outbox.getProcessingLease();
        if (!processingLease.isClaimed()) {
            return false;
        }

        return processingLease.startedAt().equals(claimedProcessingStartedAt);
    }
}

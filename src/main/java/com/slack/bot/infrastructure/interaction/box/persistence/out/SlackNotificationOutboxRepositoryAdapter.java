package com.slack.bot.infrastructure.interaction.box.persistence.out;

import static com.slack.bot.infrastructure.interaction.box.out.QSlackNotificationOutbox.slackNotificationOutbox;

import com.querydsl.core.Tuple;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.slack.bot.infrastructure.common.FailureSnapshotDefaults;
import com.slack.bot.infrastructure.interaction.box.SlackInteractionFailureType;
import com.slack.bot.infrastructure.interaction.box.out.SlackNotificationOutbox;
import com.slack.bot.infrastructure.interaction.box.out.SlackNotificationOutboxHistory;
import com.slack.bot.infrastructure.interaction.box.out.SlackNotificationOutboxStatus;
import com.slack.bot.infrastructure.interaction.box.out.repository.SlackNotificationOutboxRepository;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@RequiredArgsConstructor
public class SlackNotificationOutboxRepositoryAdapter implements SlackNotificationOutboxRepository {

    private final JPAQueryFactory queryFactory;
    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;
    private final JpaSlackNotificationOutboxRepository repository;
    private final JpaSlackNotificationOutboxHistoryRepository historyRepository;

    @Override
    public boolean enqueue(SlackNotificationOutbox outbox) {
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("messageType", outbox.getMessageType().name())
                .addValue("idempotencyKey", outbox.getIdempotencyKey())
                .addValue("teamId", outbox.getTeamId())
                .addValue("channelId", outbox.getChannelId())
                .addValue("userId", outbox.getUserId())
                .addValue("text", outbox.getText())
                .addValue("blocksJson", outbox.getBlocksJson())
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
    public SlackNotificationOutbox save(SlackNotificationOutbox outbox) {
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
                .addValue("processingStatus", SlackNotificationOutboxStatus.PROCESSING.name())
                .addValue("currentProcessingStartedAt", Timestamp.from(currentProcessingStartedAt));

        int updatedCount = namedParameterJdbcTemplate.update(
                """
                UPDATE slack_notification_outbox
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

        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("status", outbox.getStatus().name())
                .addValue("processingStartedAt", toTimestamp(outbox.getProcessingStartedAt()))
                .addValue("sentAt", toTimestamp(outbox.getSentAt()))
                .addValue("failedAt", toTimestamp(outbox.getFailedAt()))
                .addValue("failureReason", outbox.getFailureReason())
                .addValue("failureType", resolveFailureTypeName(outbox))
                .addValue("outboxId", outbox.getId())
                .addValue("processingStatus", SlackNotificationOutboxStatus.PROCESSING.name())
                .addValue("claimedProcessingStartedAt", Timestamp.from(claimedProcessingStartedAt));

        int updatedCount = namedParameterJdbcTemplate.update(
                """
                UPDATE slack_notification_outbox
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
    public Optional<SlackNotificationOutbox> findById(Long outboxId) {
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
                                SlackNotificationOutboxStatus.PENDING.name(),
                                SlackNotificationOutboxStatus.RETRY_PENDING.name()
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
                .addValue("processingStatus", SlackNotificationOutboxStatus.PROCESSING.name())
                .addValue("processingStartedAt", Timestamp.from(processingStartedAt))
                .addValue("outboxId", outboxId);

        int updatedCount = namedParameterJdbcTemplate.update(
                """
                UPDATE slack_notification_outbox
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
                FROM slack_notification_outbox
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
        validateRecoverTimeoutProcessingArguments(
                processingStartedBefore,
                failedAt,
                failureReason,
                maxAttempts,
                recoveryBatchSize
        );

        BooleanExpression timeoutCondition = slackNotificationOutbox.processingStartedAt.lt(processingStartedBefore);

        int exhaustedCount = recoverTimeoutProcessingByStatus(
                timeoutCondition,
                slackNotificationOutbox.processingAttempt.goe(maxAttempts),
                SlackNotificationOutboxStatus.FAILED,
                failedAt,
                failureReason,
                SlackInteractionFailureType.RETRY_EXHAUSTED,
                recoveryBatchSize
        );
        int recoveredCount = recoverTimeoutProcessingByStatus(
                timeoutCondition,
                slackNotificationOutbox.processingAttempt.lt(maxAttempts),
                SlackNotificationOutboxStatus.RETRY_PENDING,
                failedAt,
                failureReason,
                SlackInteractionFailureType.NONE,
                recoveryBatchSize
        );

        return exhaustedCount + recoveredCount;
    }

    private int recoverTimeoutProcessingByStatus(
            BooleanExpression timeoutCondition,
            BooleanExpression attemptCondition,
            SlackNotificationOutboxStatus targetStatus,
            Instant failedAt,
            String failureReason,
            SlackInteractionFailureType failureType,
            int recoveryBatchSize
    ) {
        List<Tuple> timedOutRows = queryFactory
                .select(slackNotificationOutbox.id, slackNotificationOutbox.processingAttempt)
                .from(slackNotificationOutbox)
                .where(
                        slackNotificationOutbox.status.eq(SlackNotificationOutboxStatus.PROCESSING),
                        timeoutCondition,
                        attemptCondition
                )
                .orderBy(slackNotificationOutbox.processingStartedAt.asc(), slackNotificationOutbox.id.asc())
                .limit(recoveryBatchSize)
                .fetch();

        if (timedOutRows.isEmpty()) {
            return 0;
        }

        List<Long> outboxIds = new ArrayList<>();
        for (Tuple row : timedOutRows) {
            outboxIds.add(row.get(slackNotificationOutbox.id));
        }

        LocalDateTime recoveryUpdatedAt = LocalDateTime.ofInstant(failedAt, ZoneOffset.UTC);
        long updatedCount = queryFactory
                .update(slackNotificationOutbox)
                .set(slackNotificationOutbox.status, targetStatus)
                .set(slackNotificationOutbox.updatedAt, recoveryUpdatedAt)
                .set(
                        slackNotificationOutbox.processingStartedAt,
                        FailureSnapshotDefaults.NO_PROCESSING_STARTED_AT
                )
                .set(slackNotificationOutbox.sentAt, FailureSnapshotDefaults.NO_SENT_AT)
                .set(slackNotificationOutbox.failedAt, failedAt)
                .set(slackNotificationOutbox.failureReason, failureReason)
                .set(slackNotificationOutbox.failureType, failureType)
                .where(
                        slackNotificationOutbox.id.in(outboxIds),
                        slackNotificationOutbox.status.eq(SlackNotificationOutboxStatus.PROCESSING),
                        timeoutCondition,
                        attemptCondition
                )
                .execute();
        if (updatedCount == 0) {
            return 0;
        }

        List<Long> recoveredOutboxIds = queryFactory
                .select(slackNotificationOutbox.id)
                .from(slackNotificationOutbox)
                .where(
                        slackNotificationOutbox.id.in(outboxIds),
                        slackNotificationOutbox.status.eq(targetStatus),
                        slackNotificationOutbox.updatedAt.eq(recoveryUpdatedAt)
                )
                .fetch();
        if (recoveredOutboxIds.isEmpty()) {
            return 0;
        }

        batchInsertTimeoutRecoveryHistory(
                timedOutRows,
                recoveredOutboxIds,
                targetStatus,
                failedAt,
                failureReason,
                failureType
        );

        return recoveredOutboxIds.size();
    }

    private void batchInsertTimeoutRecoveryHistory(
            List<Tuple> timedOutRows,
            List<Long> recoveredOutboxIds,
            SlackNotificationOutboxStatus targetStatus,
            Instant failedAt,
            String failureReason,
            SlackInteractionFailureType failureType
    ) {
        Set<Long> recoveredOutboxIdSet = new HashSet<>(recoveredOutboxIds);
        List<MapSqlParameterSource> batchParameters = new ArrayList<>();

        for (Tuple row : timedOutRows) {
            Long outboxId = row.get(slackNotificationOutbox.id);
            if (!recoveredOutboxIdSet.contains(outboxId)) {
                continue;
            }

            Integer processingAttempt = row.get(slackNotificationOutbox.processingAttempt);
            batchParameters.add(new MapSqlParameterSource()
                    .addValue("createdAt", Timestamp.from(failedAt))
                    .addValue("updatedAt", Timestamp.from(failedAt))
                    .addValue("outboxId", outboxId)
                    .addValue("processingAttempt", processingAttempt)
                    .addValue("status", targetStatus.name())
                    .addValue("completedAt", Timestamp.from(failedAt))
                    .addValue("failureReason", failureReason)
                    .addValue("failureType", failureType.name()));
        }

        namedParameterJdbcTemplate.batchUpdate(
                """
                INSERT INTO slack_notification_outbox_history (
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

    protected String buildEnqueueSql() {
        return """
                INSERT INTO slack_notification_outbox (
                    created_at,
                    updated_at,
                    message_type,
                    idempotency_key,
                    team_id,
                    channel_id,
                    user_id,
                    text,
                    blocks_json,
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
                    :teamId,
                    :channelId,
                    :userId,
                    :text,
                    :blocksJson,
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

    private Timestamp toTimestamp(Instant instant) {
        return Timestamp.from(instant);
    }

    private String resolveFailureTypeName(SlackNotificationOutbox outbox) {
        return outbox.getFailureType().name();
    }
}

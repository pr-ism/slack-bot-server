package com.slack.bot.infrastructure.interaction.box.persistence.in;

import static com.slack.bot.infrastructure.interaction.box.in.QSlackInteractionInbox.slackInteractionInbox;

import com.slack.bot.infrastructure.common.BoxEventTimeState;
import com.slack.bot.infrastructure.common.BoxFailureState;
import com.slack.bot.infrastructure.common.BoxProcessingLease;
import com.slack.bot.infrastructure.common.BoxProcessingLeaseState;
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
import java.util.concurrent.atomic.AtomicInteger;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@RequiredArgsConstructor
public class SlackInteractionInboxRepositoryAdapter implements SlackInteractionInboxRepository {

    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;
    private final JpaSlackInteractionInboxRepository repository;
    private final JpaSlackInteractionInboxHistoryRepository historyRepository;

    @Override
    public boolean enqueue(SlackInteractionInboxType interactionType, String idempotencyKey, String payloadJson) {
        SlackInteractionInbox inbox = SlackInteractionInbox.pending(interactionType, idempotencyKey, payloadJson);
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("interactionType", inbox.getInteractionType().name())
                .addValue("idempotencyKey", inbox.getIdempotencyKey())
                .addValue("payloadJson", inbox.getPayloadJson())
                .addValue("pendingStatus", inbox.getStatus().name())
                .addValue("processingAttempt", inbox.getProcessingAttempt())
                .addValue("idleLeaseState", BoxProcessingLeaseState.IDLE.name())
                .addValue("absentTimeState", BoxEventTimeState.ABSENT.name())
                .addValue("absentFailureState", BoxFailureState.ABSENT.name());

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

        MapSqlParameterSource selectParameters = new MapSqlParameterSource()
                .addValue("interactionType", interactionType.name())
                .addValue(
                        "claimableStatuses",
                        List.of(
                                SlackInteractionInboxStatus.PENDING.name(),
                                SlackInteractionInboxStatus.RETRY_PENDING.name()
                        )
                );
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
        return repository.findLockedById(inboxId)
                .flatMap(inbox -> {
                    inbox.claim(processingStartedAt);
                    repository.save(inbox);
                    return Optional.of(inbox.getId());
                });
    }

    private String buildClaimNextIdSelectSql(Collection<Long> excludedInboxIds) {
        StringBuilder sql = new StringBuilder(
                """
                SELECT id
                FROM slack_interaction_inbox
                WHERE interaction_type = :interactionType
                  AND status IN (:claimableStatuses)
                """
        );

        appendExcludedInboxIdsClause(sql, excludedInboxIds);
        sql.append(
                """
                ORDER BY id ASC
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
    public Optional<SlackInteractionInbox> findById(Long inboxId) {
        return repository.findById(inboxId);
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

        List<Long> timedOutInboxIds = selectTimeoutRecoveryTargetIds(
                interactionType,
                processingStartedBefore,
                recoveryBatchSize
        );

        AtomicInteger recoveredCount = new AtomicInteger();
        for (Long inboxId : timedOutInboxIds) {
            repository.findLockedById(inboxId)
                    .filter(inbox -> isTimeoutRecoverable(inbox, interactionType, processingStartedBefore))
                    .ifPresent(inbox -> {
                        SlackInteractionInboxHistory history = recoverTimeoutProcessing(
                                inbox,
                                failedAt,
                                failureReason,
                                maxAttempts
                        );

                        repository.save(inbox);
                        historyRepository.save(history.bindInboxId(inbox.getId()));
                        recoveredCount.incrementAndGet();
                    });
        }

        return recoveredCount.get();
    }

    private List<Long> selectTimeoutRecoveryTargetIds(
            SlackInteractionInboxType interactionType,
            Instant processingStartedBefore,
            int recoveryBatchSize
    ) {
        return namedParameterJdbcTemplate.query(
                buildTimeoutRecoverySelectSql(recoveryBatchSize),
                new MapSqlParameterSource()
                        .addValue("interactionType", interactionType.name())
                        .addValue("processingStatus", SlackInteractionInboxStatus.PROCESSING.name())
                        .addValue("processingStartedBefore", Timestamp.from(processingStartedBefore)),
                (resultSet, rowNum) -> resultSet.getLong("id")
        );
    }

    private String buildTimeoutRecoverySelectSql(int recoveryBatchSize) {
        StringBuilder sql = new StringBuilder(
                """
                SELECT inbox.id
                FROM slack_interaction_inbox inbox
                JOIN slack_interaction_inbox_processing_lease_details lease
                  ON lease.owner_id = inbox.id
                WHERE inbox.interaction_type = :interactionType
                  AND inbox.status = :processingStatus
                  AND lease.started_at < :processingStartedBefore
                ORDER BY lease.started_at ASC, inbox.id ASC
                LIMIT
                """
        );
        sql.append(recoveryBatchSize);
        sql.append("\nFOR UPDATE SKIP LOCKED");
        return sql.toString();
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
                    processing_lease_state,
                    processed_time_state,
                    failed_time_state,
                    failure_state
                )
                VALUES (
                    CURRENT_TIMESTAMP(6),
                    CURRENT_TIMESTAMP(6),
                    :interactionType,
                    :idempotencyKey,
                    :payloadJson,
                    :pendingStatus,
                    :processingAttempt,
                    :idleLeaseState,
                    :absentTimeState,
                    :absentTimeState,
                    :absentFailureState
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

    @Override
    @Transactional
    public SlackInteractionInbox save(SlackInteractionInbox inbox) {
        return repository.save(inbox);
    }

    @Override
    @Transactional
    public SlackInteractionInbox save(SlackInteractionInbox inbox, SlackInteractionInboxHistory history) {
        SlackInteractionInbox saved = repository.save(inbox);
        if (history != null) {
            historyRepository.save(history.bindInboxId(saved.getId()));
        }

        return saved;
    }
}

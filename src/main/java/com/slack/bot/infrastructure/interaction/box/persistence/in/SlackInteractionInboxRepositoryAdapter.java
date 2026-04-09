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
                .flatMap(entity -> {
                    SlackInteractionInbox inbox = entity.toDomain();
                    inbox.claim(processingStartedAt);
                    entity.apply(inbox);
                    SlackInteractionInboxJpaEntity saved = repository.save(entity);
                    return Optional.of(saved.getId());
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
        return repository.findDomainById(inboxId);
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
                    .map(entity -> TimeoutRecoveryCandidate.of(entity, entity.toDomain()))
                    .filter(candidate -> isTimeoutRecoverable(candidate.inbox(), interactionType, processingStartedBefore))
                    .ifPresent(candidate -> {
                        SlackInteractionInboxHistory history = recoverTimeoutProcessing(
                                candidate.inbox(),
                                failedAt,
                                failureReason,
                                maxAttempts
                        );

                        candidate.entity().apply(candidate.inbox());
                        repository.save(candidate.entity());
                        saveHistory(history.bindInboxId(candidate.inbox().getId()));
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
                SELECT id
                FROM slack_interaction_inbox
                WHERE interaction_type = :interactionType
                  AND status = :processingStatus
                  AND processing_started_at < :processingStartedBefore
                ORDER BY processing_started_at ASC, id ASC
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
        SlackInteractionInboxJpaEntity entity = findInboxEntity(inbox.getId()).orElseGet(
                () -> new SlackInteractionInboxJpaEntity()
        );
        entity.apply(inbox);
        return repository.save(entity).toDomain();
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

        Timestamp processingStartedAt = null;
        if (inbox.getProcessingLease().isClaimed()) {
            processingStartedAt = Timestamp.from(inbox.getProcessingLease().startedAt());
        }

        Timestamp processedAt = null;
        if (inbox.getProcessedTime().isPresent()) {
            processedAt = Timestamp.from(inbox.getProcessedTime().occurredAt());
        }

        Timestamp failedAt = null;
        if (inbox.getFailedTime().isPresent()) {
            failedAt = Timestamp.from(inbox.getFailedTime().occurredAt());
        }

        String failureReason = null;
        String failureType = null;
        if (inbox.getFailure().isPresent()) {
            failureReason = inbox.getFailure().reason();
            failureType = inbox.getFailure().type().name();
        }

        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("status", inbox.getStatus().name())
                .addValue("processingStartedAt", processingStartedAt)
                .addValue("processedAt", processedAt)
                .addValue("failedAt", failedAt)
                .addValue("failureReason", failureReason)
                .addValue("failureType", failureType)
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

    private Optional<SlackInteractionInboxJpaEntity> findInboxEntity(Long inboxId) {
        if (inboxId == null) {
            return Optional.empty();
        }

        return repository.findById(inboxId);
    }

    private void saveHistory(SlackInteractionInboxHistory history) {
        SlackInteractionInboxHistoryJpaEntity entity = new SlackInteractionInboxHistoryJpaEntity();
        entity.apply(history);
        historyRepository.save(entity);
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

    private record TimeoutRecoveryCandidate(
            SlackInteractionInboxJpaEntity entity,
            SlackInteractionInbox inbox
    ) {
        private static TimeoutRecoveryCandidate of(
                SlackInteractionInboxJpaEntity entity,
                SlackInteractionInbox inbox
        ) {
            return new TimeoutRecoveryCandidate(entity, inbox);
        }
    }
}

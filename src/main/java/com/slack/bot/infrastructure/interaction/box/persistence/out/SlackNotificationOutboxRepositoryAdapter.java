package com.slack.bot.infrastructure.interaction.box.persistence.out;

import com.slack.bot.infrastructure.common.BoxEventTimeState;
import com.slack.bot.infrastructure.common.BoxFailureState;
import com.slack.bot.infrastructure.common.BoxProcessingLease;
import com.slack.bot.infrastructure.common.BoxProcessingLeaseState;
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
import java.util.concurrent.atomic.AtomicInteger;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@RequiredArgsConstructor
public class SlackNotificationOutboxRepositoryAdapter implements SlackNotificationOutboxRepository {

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
                .addValue("userIdState", outbox.getUserId().getState().name())
                .addValue("userId", outbox.getUserId().valueOrBlank())
                .addValue("textState", outbox.getText().getState().name())
                .addValue("text", outbox.getText().valueOrBlank())
                .addValue("blocksJsonState", outbox.getBlocksJson().getState().name())
                .addValue("blocksJson", outbox.getBlocksJson().valueOrBlank())
                .addValue("fallbackTextState", outbox.getFallbackText().getState().name())
                .addValue("fallbackText", outbox.getFallbackText().valueOrBlank())
                .addValue("pendingStatus", outbox.getStatus().name())
                .addValue("processingAttempt", outbox.getProcessingAttempt())
                .addValue("processingLeaseState", BoxProcessingLeaseState.IDLE.name())
                .addValue("sentTimeState", BoxEventTimeState.ABSENT.name())
                .addValue("failedTimeState", BoxEventTimeState.ABSENT.name())
                .addValue("failureState", BoxFailureState.ABSENT.name());

        int updatedCount = namedParameterJdbcTemplate.update(buildEnqueueSql(), parameters);

        return updatedCount > 0;
    }

    @Override
    @Transactional
    public SlackNotificationOutbox save(SlackNotificationOutbox outbox) {
        SlackNotificationOutboxJpaEntity entity = findOutboxEntity(outbox);
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

        return repository.findLockedById(outboxId)
                .map(entity -> renewProcessingLease(
                        entity,
                        currentProcessingStartedAt,
                        renewedProcessingStartedAt
                ))
                .orElse(false);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<SlackNotificationOutbox> findById(Long outboxId) {
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
        return repository.findLockedById(outboxId)
                .flatMap(entity -> {
                    SlackNotificationOutbox outbox = entity.toDomain();
                    outbox.claim(processingStartedAt);
                    entity.apply(outbox);
                    SlackNotificationOutboxJpaEntity saved = repository.save(entity);
                    return Optional.of(saved.getId());
                });
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

        List<Long> timedOutOutboxIds = selectTimeoutRecoveryTargetIds(
                processingStartedBefore,
                recoveryBatchSize
        );

        AtomicInteger recoveredCount = new AtomicInteger();
        for (Long outboxId : timedOutOutboxIds) {
            repository.findLockedById(outboxId)
                    .map(entity -> TimeoutRecoveryCandidate.of(entity, entity.toDomain()))
                    .filter(candidate -> isTimeoutRecoverable(candidate.outbox(), processingStartedBefore))
                    .ifPresent(candidate -> {
                        SlackNotificationOutboxHistory history = recoverTimeoutProcessing(
                                candidate.outbox(),
                                failedAt,
                                failureReason,
                                maxAttempts
                        );

                        candidate.entity().apply(candidate.outbox());
                        repository.save(candidate.entity());
                        saveHistory(history);
                        recoveredCount.incrementAndGet();
                    });
        }

        return recoveredCount.get();
    }

    private List<Long> selectTimeoutRecoveryTargetIds(
            Instant processingStartedBefore,
            int recoveryBatchSize
    ) {
        return namedParameterJdbcTemplate.query(
                buildTimeoutRecoverySelectSql(recoveryBatchSize),
                new MapSqlParameterSource()
                        .addValue("processingStatus", SlackNotificationOutboxStatus.PROCESSING.name())
                        .addValue("processingStartedBefore", Timestamp.from(processingStartedBefore)),
                (resultSet, rowNum) -> resultSet.getLong("id")
        );
    }

    private String buildTimeoutRecoverySelectSql(int recoveryBatchSize) {
        StringBuilder sql = new StringBuilder(
                """
                SELECT id
                FROM slack_notification_outbox
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
        return saveIfProcessingLeaseMatched(outbox, SlackNotificationOutboxHistoryHolder.absent(), claimedProcessingStartedAt);
    }

    @Override
    @Transactional
    public boolean saveIfProcessingLeaseMatched(
            SlackNotificationOutbox outbox,
            SlackNotificationOutboxHistory history,
            Instant claimedProcessingStartedAt
    ) {
        SlackNotificationOutboxHistoryHolder historyHolder = SlackNotificationOutboxHistoryHolder.of(history);
        return saveIfProcessingLeaseMatched(outbox, historyHolder, claimedProcessingStartedAt);
    }

    private boolean saveIfProcessingLeaseMatched(
            SlackNotificationOutbox outbox,
            SlackNotificationOutboxHistoryHolder historyHolder,
            Instant claimedProcessingStartedAt
    ) {
        validateSaveIfProcessingLeaseMatchedArguments(outbox, claimedProcessingStartedAt);
        return repository.findLockedById(outbox.getId())
                .filter(entity -> {
                    SlackNotificationOutbox persistedOutbox = entity.toDomain();
                    return persistedOutbox.getStatus() == SlackNotificationOutboxStatus.PROCESSING
                            && hasClaimedLease(persistedOutbox, claimedProcessingStartedAt);
                })
                .map(entity -> {
                    entity.apply(outbox);
                    repository.save(entity);

                    if (historyHolder.isPresent()) {
                        saveHistory(historyHolder.value());
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

    private boolean hasClaimedLease(SlackNotificationOutbox outbox, Instant claimedProcessingStartedAt) {
        BoxProcessingLease processingLease = outbox.getProcessingLease();
        if (!processingLease.isClaimed()) {
            return false;
        }

        return processingLease.startedAt().equals(claimedProcessingStartedAt);
    }

    private boolean renewProcessingLease(
            SlackNotificationOutboxJpaEntity entity,
            Instant currentProcessingStartedAt,
            Instant renewedProcessingStartedAt
    ) {
        SlackNotificationOutbox outbox = entity.toDomain();
        if (outbox.getStatus() != SlackNotificationOutboxStatus.PROCESSING) {
            return false;
        }
        if (!hasClaimedLease(outbox, currentProcessingStartedAt)) {
            return false;
        }

        outbox.renewProcessingLease(renewedProcessingStartedAt);
        entity.apply(outbox);
        repository.save(entity);
        return true;
    }

    private SlackNotificationOutboxJpaEntity findOutboxEntity(SlackNotificationOutbox outbox) {
        if (!outbox.hasId()) {
            return new SlackNotificationOutboxJpaEntity();
        }

        return repository.findById(outbox.getId())
                .orElseThrow(() -> new IllegalStateException("저장 대상 outbox를 찾을 수 없습니다. id=" + outbox.getId()));
    }

    private void saveHistory(SlackNotificationOutboxHistory history) {
        SlackNotificationOutboxHistoryJpaEntity entity = new SlackNotificationOutboxHistoryJpaEntity();
        entity.apply(history);
        historyRepository.save(entity);
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

    private record TimeoutRecoveryCandidate(
            SlackNotificationOutboxJpaEntity entity,
            SlackNotificationOutbox outbox
    ) {
        private static TimeoutRecoveryCandidate of(
                SlackNotificationOutboxJpaEntity entity,
                SlackNotificationOutbox outbox
        ) {
            return new TimeoutRecoveryCandidate(entity, outbox);
        }
    }

    private sealed interface SlackNotificationOutboxHistoryHolder
            permits AbsentSlackNotificationOutboxHistoryHolder, PresentSlackNotificationOutboxHistoryHolder {

        static SlackNotificationOutboxHistoryHolder absent() {
            return AbsentSlackNotificationOutboxHistoryHolder.INSTANCE;
        }

        static SlackNotificationOutboxHistoryHolder of(SlackNotificationOutboxHistory history) {
            if (history == null) {
                return absent();
            }

            return new PresentSlackNotificationOutboxHistoryHolder(history);
        }

        boolean isPresent();

        default SlackNotificationOutboxHistory value() {
            throw new IllegalStateException("history가 없는 상태입니다.");
        }
    }

    private static final class AbsentSlackNotificationOutboxHistoryHolder
            implements SlackNotificationOutboxHistoryHolder {

        private static final AbsentSlackNotificationOutboxHistoryHolder INSTANCE =
                new AbsentSlackNotificationOutboxHistoryHolder();

        private AbsentSlackNotificationOutboxHistoryHolder() {
        }

        @Override
        public boolean isPresent() {
            return false;
        }
    }

    private record PresentSlackNotificationOutboxHistoryHolder(
            SlackNotificationOutboxHistory value
    ) implements SlackNotificationOutboxHistoryHolder {

        private PresentSlackNotificationOutboxHistoryHolder {
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

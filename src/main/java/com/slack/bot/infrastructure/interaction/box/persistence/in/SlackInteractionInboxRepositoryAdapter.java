package com.slack.bot.infrastructure.interaction.box.persistence.in;

import static com.slack.bot.infrastructure.interaction.box.in.QSlackInteractionInbox.slackInteractionInbox;

import com.querydsl.core.Tuple;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
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

    private final JPAQueryFactory queryFactory;
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
                .addValue("noFailureAt", Timestamp.from(SlackInteractionInbox.NO_FAILURE_AT))
                .addValue("noFailureReason", SlackInteractionInbox.NO_FAILURE_REASON)
                .addValue("noneFailureType", SlackInteractionFailureType.NONE.name());

        int updatedCount = namedParameterJdbcTemplate.update(
                buildEnqueueSql(),
                parameters
        );

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
        MapSqlParameterSource updateParameters = new MapSqlParameterSource()
                .addValue("processingStatus", SlackInteractionInboxStatus.PROCESSING.name())
                .addValue("processingStartedAt", Timestamp.from(processingStartedAt))
                .addValue("inboxId", inboxId);

        int updatedCount = namedParameterJdbcTemplate.update(
                """
                    UPDATE slack_interaction_inbox
                    SET status = :processingStatus,
                        processing_attempt = processing_attempt + 1,
                        processing_started_at = :processingStartedAt,
                        failed_at = :noFailureAt,
                        failure_reason = :noFailureReason,
                        failure_type = :noneFailureType
                    WHERE id = :inboxId
                    """,
                updateParameters
                        .addValue("noFailureAt", Timestamp.from(SlackInteractionInbox.NO_FAILURE_AT))
                        .addValue("noFailureReason", SlackInteractionInbox.NO_FAILURE_REASON)
                        .addValue("noneFailureType", SlackInteractionFailureType.NONE.name())
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
            int maxAttempts
    ) {
        validateRecoverTimeoutProcessingArguments(processingStartedBefore, failedAt, failureReason, maxAttempts);

        BooleanExpression timeoutCondition = slackInteractionInbox.processingStartedAt.isNull()
                                                                                      .or(slackInteractionInbox.processingStartedAt.lt(
                                                                                              processingStartedBefore
                                                                                      ));

        int exhaustedCount = recoverTimeoutProcessingByStatus(
                interactionType,
                timeoutCondition,
                slackInteractionInbox.processingAttempt.goe(maxAttempts),
                SlackInteractionInboxStatus.FAILED,
                failedAt,
                failureReason,
                SlackInteractionFailureType.RETRY_EXHAUSTED
        );
        int recoveredCount = recoverTimeoutProcessingByStatus(
                interactionType,
                timeoutCondition,
                slackInteractionInbox.processingAttempt.lt(maxAttempts),
                SlackInteractionInboxStatus.RETRY_PENDING,
                failedAt,
                failureReason,
                SlackInteractionFailureType.NONE
        );

        return exhaustedCount + recoveredCount;
    }

    private int recoverTimeoutProcessingByStatus(
            SlackInteractionInboxType interactionType,
            BooleanExpression timeoutCondition,
            BooleanExpression attemptCondition,
            SlackInteractionInboxStatus targetStatus,
            Instant failedAt,
            String failureReason,
            SlackInteractionFailureType failureType
    ) {
        List<Tuple> timedOutRows = queryFactory
                .select(slackInteractionInbox.id, slackInteractionInbox.processingAttempt)
                .from(slackInteractionInbox)
                .where(
                        slackInteractionInbox.interactionType.eq(interactionType),
                        slackInteractionInbox.status.eq(SlackInteractionInboxStatus.PROCESSING),
                        timeoutCondition,
                        attemptCondition
                )
                .fetch();

        int recoveredCount = 0;
        for (Tuple row : timedOutRows) {
            Long inboxId = row.get(slackInteractionInbox.id);
            Integer processingAttempt = row.get(slackInteractionInbox.processingAttempt);
            long updatedCount = queryFactory
                    .update(slackInteractionInbox)
                    .set(slackInteractionInbox.status, targetStatus)
                    .set(slackInteractionInbox.processingStartedAt, (Instant) null)
                    .set(slackInteractionInbox.failedAt, failedAt)
                    .set(slackInteractionInbox.failureReason, failureReason)
                    .set(slackInteractionInbox.failureType, failureType)
                    .where(
                            slackInteractionInbox.id.eq(inboxId),
                            slackInteractionInbox.interactionType.eq(interactionType),
                            slackInteractionInbox.status.eq(SlackInteractionInboxStatus.PROCESSING),
                            timeoutCondition,
                            attemptCondition
                    )
                    .execute();
            if (updatedCount == 0) {
                continue;
            }

            historyRepository.save(
                    SlackInteractionInboxHistory.completed(
                            inboxId,
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

    private void validateRecoverTimeoutProcessingArguments(
            Instant processingStartedBefore,
            Instant failedAt,
            String failureReason,
            int maxAttempts
    ) {
        validateProcessingStartedBefore(processingStartedBefore);
        validateFailedAt(failedAt);
        validateFailureReason(failureReason);
        validateMaxAttempts(maxAttempts);
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
                    :noneFailureType
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

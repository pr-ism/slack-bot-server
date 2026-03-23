package com.slack.bot.infrastructure.interaction.box.persistence.out;

import static com.slack.bot.infrastructure.interaction.box.out.QSlackNotificationOutbox.slackNotificationOutbox;

import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.slack.bot.infrastructure.interaction.box.SlackInteractionFailureType;
import com.slack.bot.infrastructure.interaction.box.out.SlackNotificationOutbox;
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

    private final JPAQueryFactory queryFactory;
    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;
    private final JpaSlackNotificationOutboxRepository repository;

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
                .addValue("processingAttempt", outbox.getProcessingAttempt());

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
            int maxAttempts
    ) {
        validateRecoverTimeoutProcessingArguments(processingStartedBefore, failedAt, failureReason, maxAttempts);

        BooleanExpression timeoutCondition = slackNotificationOutbox.processingStartedAt.isNull()
                                                                                          .or(slackNotificationOutbox.processingStartedAt.lt(
                                                                                                  processingStartedBefore
                                                                                          ));

        long exhaustedCount = queryFactory
                .update(slackNotificationOutbox)
                .set(slackNotificationOutbox.status, SlackNotificationOutboxStatus.FAILED)
                .set(slackNotificationOutbox.processingStartedAt, Expressions.nullExpression(Instant.class))
                .set(slackNotificationOutbox.failedAt, failedAt)
                .set(slackNotificationOutbox.failureReason, failureReason)
                .set(slackNotificationOutbox.failureType, SlackInteractionFailureType.RETRY_EXHAUSTED)
                .where(
                        slackNotificationOutbox.status.eq(SlackNotificationOutboxStatus.PROCESSING),
                        timeoutCondition,
                        slackNotificationOutbox.processingAttempt.goe(maxAttempts)
                )
                .execute();

        long recoveredCount = queryFactory
                .update(slackNotificationOutbox)
                .set(slackNotificationOutbox.status, SlackNotificationOutboxStatus.RETRY_PENDING)
                .set(slackNotificationOutbox.processingStartedAt, Expressions.nullExpression(Instant.class))
                .set(slackNotificationOutbox.failedAt, failedAt)
                .set(slackNotificationOutbox.failureReason, failureReason)
                .set(slackNotificationOutbox.failureType, Expressions.nullExpression(SlackInteractionFailureType.class))
                .where(
                        slackNotificationOutbox.status.eq(SlackNotificationOutboxStatus.PROCESSING),
                        timeoutCondition,
                        slackNotificationOutbox.processingAttempt.lt(maxAttempts)
                )
                .execute();

        return Math.toIntExact(exhaustedCount + recoveredCount);
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
                    processing_attempt
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
                    :processingAttempt
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
}

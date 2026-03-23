package com.slack.bot.infrastructure.interaction.box.persistence.in;

import static com.slack.bot.infrastructure.interaction.box.in.QSlackInteractionInbox.slackInteractionInbox;

import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.slack.bot.infrastructure.common.MysqlDuplicateKeyDetector;
import com.slack.bot.infrastructure.interaction.box.SlackInteractionFailureType;
import com.slack.bot.infrastructure.interaction.box.in.SlackInteractionInbox;
import com.slack.bot.infrastructure.interaction.box.in.SlackInteractionInboxStatus;
import com.slack.bot.infrastructure.interaction.box.in.SlackInteractionInboxType;
import com.slack.bot.infrastructure.interaction.box.in.repository.SlackInteractionInboxRepository;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
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
    private final SlackInteractionInboxCreator inboxCreator;
    private final MysqlDuplicateKeyDetector mysqlDuplicateKeyDetector;

    @Override
    public boolean enqueue(SlackInteractionInboxType interactionType, String idempotencyKey, String payloadJson) {
        SlackInteractionInbox inbox = SlackInteractionInbox.pending(interactionType, idempotencyKey, payloadJson);

        try {
            inboxCreator.saveNew(inbox);
            return true;
        } catch (DataIntegrityViolationException exception) {
            if (mysqlDuplicateKeyDetector.isNotDuplicateKey(exception)) {
                throw exception;
            }
            return false;
        }
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
                        failed_at = NULL,
                        failure_reason = NULL,
                        failure_type = NULL
                    WHERE id = :inboxId
                    """,
                updateParameters
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

        long exhaustedCount = queryFactory
                .update(slackInteractionInbox)
                .set(slackInteractionInbox.status, SlackInteractionInboxStatus.FAILED)
                .set(slackInteractionInbox.processingStartedAt, Expressions.nullExpression(Instant.class))
                .set(slackInteractionInbox.failedAt, failedAt)
                .set(slackInteractionInbox.failureReason, failureReason)
                .set(slackInteractionInbox.failureType, SlackInteractionFailureType.RETRY_EXHAUSTED)
                .where(
                        slackInteractionInbox.interactionType.eq(interactionType),
                        slackInteractionInbox.status.eq(SlackInteractionInboxStatus.PROCESSING),
                        timeoutCondition,
                        slackInteractionInbox.processingAttempt.goe(maxAttempts)
                )
                .execute();

        long recoveredCount = queryFactory
                .update(slackInteractionInbox)
                .set(slackInteractionInbox.status, SlackInteractionInboxStatus.RETRY_PENDING)
                .set(slackInteractionInbox.processingStartedAt, Expressions.nullExpression(Instant.class))
                .set(slackInteractionInbox.failedAt, failedAt)
                .set(slackInteractionInbox.failureReason, failureReason)
                .set(slackInteractionInbox.failureType, Expressions.nullExpression(SlackInteractionFailureType.class))
                .where(
                        slackInteractionInbox.interactionType.eq(interactionType),
                        slackInteractionInbox.status.eq(SlackInteractionInboxStatus.PROCESSING),
                        timeoutCondition,
                        slackInteractionInbox.processingAttempt.lt(maxAttempts)
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
}

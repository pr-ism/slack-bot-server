package com.slack.bot.infrastructure.interaction.box.persistence.out;

import static com.slack.bot.infrastructure.interaction.box.out.QSlackNotificationOutbox.slackNotificationOutbox;

import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.slack.bot.infrastructure.common.MysqlDuplicateKeyDetector;
import com.slack.bot.infrastructure.interaction.box.SlackInteractivityFailureType;
import com.slack.bot.infrastructure.interaction.box.out.SlackNotificationOutbox;
import com.slack.bot.infrastructure.interaction.box.out.SlackNotificationOutboxStatus;
import com.slack.bot.infrastructure.interaction.box.out.repository.SlackNotificationOutboxRepository;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@RequiredArgsConstructor
public class SlackNotificationOutboxRepositoryAdapter implements SlackNotificationOutboxRepository {

    private static final List<SlackNotificationOutboxStatus> PROCESSING_CLAIMABLE_STATUSES = List.of(
            SlackNotificationOutboxStatus.PENDING,
            SlackNotificationOutboxStatus.RETRY_PENDING
    );

    private final JPAQueryFactory queryFactory;
    private final JpaSlackNotificationOutboxRepository repository;
    private final MysqlDuplicateKeyDetector mysqlDuplicateKeyDetector;

    @Override
    public boolean enqueue(SlackNotificationOutbox outbox) {
        try {
            repository.save(outbox);
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
    public SlackNotificationOutbox save(SlackNotificationOutbox outbox) {
        return repository.save(outbox);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<SlackNotificationOutbox> findById(Long outboxId) {
        return repository.findById(outboxId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<SlackNotificationOutbox> findPending(int limit) {
        if (limit <= 0) {
            return Collections.emptyList();
        }

        return queryFactory
                .selectFrom(slackNotificationOutbox)
                .where(slackNotificationOutbox.status.in(PROCESSING_CLAIMABLE_STATUSES))
                .orderBy(slackNotificationOutbox.id.asc())
                .limit(limit)
                .fetch();
    }

    @Override
    @Transactional
    public boolean markProcessingIfPending(Long outboxId, Instant processingStartedAt) {
        long updatedCount = markAsProcessingWhenClaimable(outboxId, processingStartedAt);

        return updatedCount > 0;
    }

    private long markAsProcessingWhenClaimable(Long outboxId, Instant processingStartedAt) {
        return queryFactory
                .update(slackNotificationOutbox)
                .set(slackNotificationOutbox.status, SlackNotificationOutboxStatus.PROCESSING)
                .set(slackNotificationOutbox.processingStartedAt, processingStartedAt)
                .set(slackNotificationOutbox.processingAttempt, slackNotificationOutbox.processingAttempt.add(1))
                .set(slackNotificationOutbox.failedAt, Expressions.nullExpression(Instant.class))
                .set(slackNotificationOutbox.failureReason, Expressions.nullExpression(String.class))
                .set(slackNotificationOutbox.failureType, Expressions.nullExpression(SlackInteractivityFailureType.class))
                .where(
                        slackNotificationOutbox.id.eq(outboxId),
                        slackNotificationOutbox.status.in(PROCESSING_CLAIMABLE_STATUSES)
                )
                .execute();
    }

    @Override
    @Transactional
    public int recoverTimeoutProcessing(Instant processingStartedBefore, Instant failedAt, String failureReason) {
        return Math.toIntExact(queryFactory
                .update(slackNotificationOutbox)
                .set(slackNotificationOutbox.status, SlackNotificationOutboxStatus.RETRY_PENDING)
                .set(slackNotificationOutbox.processingStartedAt, Expressions.nullExpression(Instant.class))
                .set(slackNotificationOutbox.failedAt, failedAt)
                .set(slackNotificationOutbox.failureReason, failureReason)
                .set(slackNotificationOutbox.failureType, Expressions.nullExpression(SlackInteractivityFailureType.class))
                .where(
                        slackNotificationOutbox.status.eq(SlackNotificationOutboxStatus.PROCESSING),
                        slackNotificationOutbox.processingStartedAt.isNull()
                                                                  .or(slackNotificationOutbox.processingStartedAt.lt(
                                                                          processingStartedBefore
                                                                  ))
                )
                .execute());
    }
}

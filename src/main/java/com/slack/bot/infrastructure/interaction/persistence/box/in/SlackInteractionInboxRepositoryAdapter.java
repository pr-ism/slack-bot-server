package com.slack.bot.infrastructure.interaction.persistence.box.in;

import static com.slack.bot.infrastructure.interaction.box.in.QSlackInteractionInbox.slackInteractionInbox;

import com.querydsl.jpa.impl.JPAQueryFactory;
import com.slack.bot.infrastructure.common.MysqlDuplicateKeyDetector;
import com.slack.bot.infrastructure.interaction.box.SlackInteractivityFailureType;
import com.slack.bot.infrastructure.interaction.box.in.SlackInteractionInbox;
import com.slack.bot.infrastructure.interaction.box.in.SlackInteractionInboxStatus;
import com.slack.bot.infrastructure.interaction.box.in.SlackInteractionInboxType;
import com.slack.bot.infrastructure.interaction.box.in.repository.SlackInteractionInboxRepository;
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
public class SlackInteractionInboxRepositoryAdapter implements SlackInteractionInboxRepository {

    private static final List<SlackInteractionInboxStatus> PROCESSING_CLAIMABLE_STATUSES = List.of(
            SlackInteractionInboxStatus.PENDING,
            SlackInteractionInboxStatus.RETRY_PENDING
    );

    private final JPAQueryFactory queryFactory;
    private final JpaSlackInteractionInboxRepository repository;
    private final MysqlDuplicateKeyDetector mysqlDuplicateKeyDetector;

    @Override
    public boolean enqueue(SlackInteractionInboxType interactionType, String idempotencyKey, String payloadJson) {
        SlackInteractionInbox inbox = SlackInteractionInbox.pending(interactionType, idempotencyKey, payloadJson);

        try {
            repository.save(inbox);
            return true;
        } catch (DataIntegrityViolationException exception) {
            if (mysqlDuplicateKeyDetector.isNotDuplicateKey(exception)) {
                throw exception;
            }
            return false;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<SlackInteractionInbox> findPending(SlackInteractionInboxType interactionType, int limit) {
        if (limit <= 0) {
            return Collections.emptyList();
        }

        return queryFactory
                .selectFrom(slackInteractionInbox)
                .where(
                        slackInteractionInbox.interactionType.eq(interactionType),
                        slackInteractionInbox.status.in(PROCESSING_CLAIMABLE_STATUSES)
                )
                .orderBy(slackInteractionInbox.id.asc())
                .limit(limit)
                .fetch();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<SlackInteractionInbox> findById(Long inboxId) {
        return repository.findById(inboxId);
    }

    @Override
    @Transactional
    public boolean markProcessingIfPending(Long inboxId, Instant processingStartedAt) {
        long updatedCount = markAsProcessingWhenClaimable(inboxId, processingStartedAt);

        return updatedCount > 0;
    }

    private long markAsProcessingWhenClaimable(Long inboxId, Instant processingStartedAt) {
        return queryFactory
                .update(slackInteractionInbox)
                .set(slackInteractionInbox.status, SlackInteractionInboxStatus.PROCESSING)
                .set(slackInteractionInbox.processingAttempt, slackInteractionInbox.processingAttempt.add(1))
                .set(slackInteractionInbox.processingStartedAt, processingStartedAt)
                .set(slackInteractionInbox.failedAt, (Instant) null)
                .set(slackInteractionInbox.failureReason, (String) null)
                .set(slackInteractionInbox.failureType, (SlackInteractivityFailureType) null)
                .where(
                        slackInteractionInbox.id.eq(inboxId),
                        slackInteractionInbox.status.in(PROCESSING_CLAIMABLE_STATUSES)
                )
                .execute();
    }

    @Override
    @Transactional
    public int recoverTimeoutProcessing(
            SlackInteractionInboxType interactionType,
            Instant processingStartedBefore,
            Instant failedAt,
            String failureReason
    ) {
        return Math.toIntExact(queryFactory
                .update(slackInteractionInbox)
                .set(slackInteractionInbox.status, SlackInteractionInboxStatus.RETRY_PENDING)
                .set(slackInteractionInbox.processingStartedAt, (Instant) null)
                .set(slackInteractionInbox.failedAt, failedAt)
                .set(slackInteractionInbox.failureReason, failureReason)
                .set(slackInteractionInbox.failureType, (SlackInteractivityFailureType) null)
                .where(
                        slackInteractionInbox.interactionType.eq(interactionType),
                        slackInteractionInbox.status.eq(SlackInteractionInboxStatus.PROCESSING),
                        slackInteractionInbox.processingStartedAt.isNull()
                                                             .or(slackInteractionInbox.processingStartedAt.lt(
                                                                     processingStartedBefore
                                                             ))
                )
                .execute());
    }

    @Override
    @Transactional
    public SlackInteractionInbox save(SlackInteractionInbox inbox) {
        return repository.save(inbox);
    }
}

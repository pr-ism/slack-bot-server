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

        List<SlackInteractionInboxStatus> retryableStatuses = List.of(
                SlackInteractionInboxStatus.PENDING,
                SlackInteractionInboxStatus.RETRY_PENDING
        );

        return queryFactory
                .selectFrom(slackInteractionInbox)
                .where(
                        slackInteractionInbox.interactionType.eq(interactionType),
                        slackInteractionInbox.status.in(retryableStatuses)
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
    public boolean markProcessingIfPending(Long inboxId) {
        long updatedCount = queryFactory
                .update(slackInteractionInbox)
                .set(slackInteractionInbox.status, SlackInteractionInboxStatus.PROCESSING)
                .set(slackInteractionInbox.processingAttempt, slackInteractionInbox.processingAttempt.add(1))
                .set(slackInteractionInbox.failedAt, (Instant) null)
                .set(slackInteractionInbox.failureReason, (String) null)
                .set(slackInteractionInbox.failureType, (SlackInteractivityFailureType) null)
                .where(
                        slackInteractionInbox.id.eq(inboxId),
                        slackInteractionInbox.status.in(
                                SlackInteractionInboxStatus.PENDING,
                                SlackInteractionInboxStatus.RETRY_PENDING
                        )
                )
                .execute();

        return updatedCount > 0;
    }

    @Override
    @Transactional
    public SlackInteractionInbox save(SlackInteractionInbox inbox) {
        return repository.save(inbox);
    }
}

package com.slack.bot.infrastructure.interaction.persistence.box.in;

import static com.slack.bot.infrastructure.interaction.box.in.QSlackInteractionInbox.slackInteractionInbox;

import com.querydsl.jpa.impl.JPAQueryFactory;
import com.slack.bot.infrastructure.common.MysqlDuplicateKeyDetector;
import com.slack.bot.infrastructure.interaction.box.in.SlackInteractionInbox;
import com.slack.bot.infrastructure.interaction.box.in.SlackInteractionInboxStatus;
import com.slack.bot.infrastructure.interaction.box.in.SlackInteractionInboxType;
import com.slack.bot.infrastructure.interaction.box.in.repository.SlackInteractionInboxRepository;
import java.util.Collections;
import java.util.List;
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
    @Transactional
    public SlackInteractionInbox save(SlackInteractionInbox inbox) {
        return repository.save(inbox);
    }
}

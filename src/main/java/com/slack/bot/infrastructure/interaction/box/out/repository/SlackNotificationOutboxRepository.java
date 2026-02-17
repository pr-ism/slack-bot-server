package com.slack.bot.infrastructure.interaction.box.out.repository;

import com.slack.bot.infrastructure.interaction.box.out.SlackNotificationOutbox;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface SlackNotificationOutboxRepository {

    boolean enqueue(SlackNotificationOutbox outbox);

    SlackNotificationOutbox save(SlackNotificationOutbox outbox);

    Optional<SlackNotificationOutbox> findById(Long outboxId);

    List<SlackNotificationOutbox> findPending(int limit);

    boolean markProcessingIfPending(Long outboxId, Instant processingStartedAt);

    int recoverTimeoutProcessing(Instant processingStartedBefore, Instant failedAt, String failureReason);
}

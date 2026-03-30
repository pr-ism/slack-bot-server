package com.slack.bot.infrastructure.interaction.box.out.repository;

import com.slack.bot.infrastructure.interaction.box.out.SlackNotificationOutbox;
import com.slack.bot.infrastructure.interaction.box.out.SlackNotificationOutboxHistory;
import java.time.Instant;
import java.util.Collection;
import java.util.Optional;

public interface SlackNotificationOutboxRepository {

    boolean enqueue(SlackNotificationOutbox outbox);

    SlackNotificationOutbox save(SlackNotificationOutbox outbox);

    boolean renewProcessingLease(
            Long outboxId,
            Instant currentProcessingStartedAt,
            Instant renewedProcessingStartedAt
    );

    boolean saveIfProcessingLeaseMatched(
            SlackNotificationOutbox outbox,
            Instant claimedProcessingStartedAt
    );

    boolean saveIfProcessingLeaseMatched(
            SlackNotificationOutbox outbox,
            SlackNotificationOutboxHistory history,
            Instant claimedProcessingStartedAt
    );

    Optional<SlackNotificationOutbox> findById(Long outboxId);

    Optional<Long> claimNextId(Instant processingStartedAt, Collection<Long> excludedOutboxIds);

    int recoverTimeoutProcessing(
            Instant processingStartedBefore,
            Instant failedAt,
            String failureReason,
            int maxAttempts
    );
}

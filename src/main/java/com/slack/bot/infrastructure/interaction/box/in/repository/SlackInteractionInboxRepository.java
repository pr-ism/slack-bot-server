package com.slack.bot.infrastructure.interaction.box.in.repository;

import com.slack.bot.infrastructure.interaction.box.in.SlackInteractionInbox;
import com.slack.bot.infrastructure.interaction.box.in.SlackInteractionInboxHistory;
import com.slack.bot.infrastructure.interaction.box.in.SlackInteractionInboxType;
import java.time.Instant;
import java.util.Collection;
import java.util.Optional;

public interface SlackInteractionInboxRepository {

    boolean enqueue(SlackInteractionInboxType interactionType, String idempotencyKey, String payloadJson);

    Optional<Long> claimNextId(
            SlackInteractionInboxType interactionType,
            Instant processingStartedAt,
            Collection<Long> excludedInboxIds
    );

    Optional<SlackInteractionInbox> findById(Long inboxId);

    int recoverTimeoutProcessing(
            SlackInteractionInboxType interactionType,
            Instant processingStartedBefore,
            Instant failedAt,
            String failureReason,
            int maxAttempts
    );

    SlackInteractionInbox save(SlackInteractionInbox inbox);

    SlackInteractionInbox save(SlackInteractionInbox inbox, SlackInteractionInboxHistory history);
}

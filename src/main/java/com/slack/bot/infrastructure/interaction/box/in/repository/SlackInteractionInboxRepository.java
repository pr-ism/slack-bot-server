package com.slack.bot.infrastructure.interaction.box.in.repository;

import com.slack.bot.infrastructure.interaction.box.in.SlackInteractionInbox;
import com.slack.bot.infrastructure.interaction.box.in.SlackInteractionInboxType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface SlackInteractionInboxRepository {

    boolean enqueue(SlackInteractionInboxType interactionType, String idempotencyKey, String payloadJson);

    List<SlackInteractionInbox> findClaimable(SlackInteractionInboxType interactionType, int limit);

    Optional<SlackInteractionInbox> findById(Long inboxId);

    boolean markProcessingIfClaimable(Long inboxId, Instant processingStartedAt);

    int recoverTimeoutProcessing(
            SlackInteractionInboxType interactionType,
            Instant processingStartedBefore,
            Instant failedAt,
            String failureReason
    );

    SlackInteractionInbox save(SlackInteractionInbox inbox);
}

package com.slack.bot.infrastructure.interaction.box.in.repository;

import com.slack.bot.infrastructure.interaction.box.in.SlackInteractionInbox;
import com.slack.bot.infrastructure.interaction.box.in.SlackInteractionInboxType;
import java.util.Optional;
import java.util.List;

public interface SlackInteractionInboxRepository {

    boolean enqueue(SlackInteractionInboxType interactionType, String idempotencyKey, String payloadJson);

    List<SlackInteractionInbox> findPending(SlackInteractionInboxType interactionType, int limit);

    Optional<SlackInteractionInbox> findById(Long inboxId);

    boolean markProcessingIfPending(Long inboxId);

    SlackInteractionInbox save(SlackInteractionInbox inbox);
}

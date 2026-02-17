package com.slack.bot.application.interactivity.box.in;

import com.slack.bot.application.interactivity.box.SlackInteractionIdempotencyKeyGenerator;
import com.slack.bot.application.interactivity.box.SlackInteractionIdempotencyScope;
import com.slack.bot.infrastructure.interaction.box.in.SlackInteractionInbox;
import com.slack.bot.infrastructure.interaction.box.in.SlackInteractionInboxType;
import com.slack.bot.infrastructure.interaction.box.in.repository.SlackInteractionInboxRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SlackInteractionInboxProcessor {

    private final SlackInteractionInboxRepository slackInteractionInboxRepository;
    private final SlackInteractionIdempotencyKeyGenerator idempotencyKeyGenerator;
    private final SlackInteractionInboxEntryProcessor slackInteractionInboxEntryProcessor;

    public boolean enqueueBlockAction(String payloadJson) {
        String idempotencyKey = idempotencyKeyGenerator.generate(SlackInteractionIdempotencyScope.BLOCK_ACTIONS, payloadJson);

        return slackInteractionInboxRepository.enqueue(
                SlackInteractionInboxType.BLOCK_ACTIONS,
                idempotencyKey,
                payloadJson
        );
    }

    public void processPendingBlockActions(int limit) {
        List<SlackInteractionInbox> pendings = slackInteractionInboxRepository.findPending(
                SlackInteractionInboxType.BLOCK_ACTIONS,
                limit
        );

        for (SlackInteractionInbox pending : pendings) {
            slackInteractionInboxEntryProcessor.processBlockAction(pending);
        }
    }

    public boolean enqueueViewSubmission(String payloadJson) {
        String idempotencyKey = idempotencyKeyGenerator.generate(SlackInteractionIdempotencyScope.VIEW_SUBMISSION, payloadJson);
        return slackInteractionInboxRepository.enqueue(
                SlackInteractionInboxType.VIEW_SUBMISSION,
                idempotencyKey,
                payloadJson
        );
    }

    public void processPendingViewSubmissions(int limit) {
        List<SlackInteractionInbox> pendings = slackInteractionInboxRepository.findPending(
                SlackInteractionInboxType.VIEW_SUBMISSION,
                limit
        );

        for (SlackInteractionInbox pending : pendings) {
            slackInteractionInboxEntryProcessor.processViewSubmission(pending);
        }
    }
}

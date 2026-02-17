package com.slack.bot.application.interactivity.box.in;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.slack.bot.application.interactivity.BlockActionInteractionService;
import com.slack.bot.application.interactivity.view.ViewSubmissionInteractionService;
import com.slack.bot.application.interactivity.box.aop.BindInboxToOutboxSource;
import com.slack.bot.application.interactivity.box.retry.InteractivityRetryExceptionClassifier;
import com.slack.bot.global.config.properties.InteractivityRetryProperties;
import com.slack.bot.infrastructure.interaction.box.SlackInteractivityFailureType;
import com.slack.bot.infrastructure.interaction.box.in.SlackInteractionInbox;
import com.slack.bot.infrastructure.interaction.box.in.SlackInteractionInboxType;
import com.slack.bot.infrastructure.interaction.box.in.repository.SlackInteractionInboxRepository;
import java.time.Clock;
import java.util.function.Consumer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class SlackInteractionInboxEntryProcessor {

    private final Clock clock;
    private final ObjectMapper objectMapper;
    private final BlockActionInteractionService blockActionInteractionService;
    private final ViewSubmissionInteractionService viewSubmissionInteractionService;
    private final RetryTemplate slackInteractionInboxRetryTemplate;
    private final InteractivityRetryProperties interactivityRetryProperties;
    private final InteractivityRetryExceptionClassifier retryExceptionClassifier;
    private final SlackInteractionInboxRepository slackInteractionInboxRepository;

    @BindInboxToOutboxSource
    public void processBlockAction(SlackInteractionInbox inbox) {
        process(
                inbox,
                payload -> blockActionInteractionService.handle(payload),
                SlackInteractionInboxType.BLOCK_ACTIONS
        );
    }

    @BindInboxToOutboxSource
    public void processViewSubmission(SlackInteractionInbox inbox) {
        process(
                inbox,
                payload -> viewSubmissionInteractionService.handleEnqueued(payload),
                SlackInteractionInboxType.VIEW_SUBMISSION
        );
    }

    private void process(
            SlackInteractionInbox inbox,
            Consumer<JsonNode> consumer,
            SlackInteractionInboxType interactionType
    ) {
        inbox.markProcessing();
        slackInteractionInboxRepository.save(inbox);

        try {
            JsonNode payload = objectMapper.readTree(inbox.getPayloadJson());

            slackInteractionInboxRetryTemplate.execute(context -> {
                consumer.accept(payload);
                return null;
            });

            inbox.markProcessed(clock.instant());
            slackInteractionInboxRepository.save(inbox);
        } catch (Exception e) {
            log.error("{} inbox 처리에 실패했습니다. inboxId={}", interactionType, inbox.getId(), e);

            markFailureStatus(inbox, e);
            slackInteractionInboxRepository.save(inbox);
        }
    }

    private void markFailureStatus(SlackInteractionInbox inbox, Exception exception) {
        String reason = exception.getMessage();

        if (!retryExceptionClassifier.isRetryable(exception)) {
            inbox.markFailed(clock.instant(), reason, SlackInteractivityFailureType.BUSINESS_INVARIANT);
            return;
        }

        if (inbox.getProcessingAttempt() < interactivityRetryProperties.inbox().maxAttempts()) {
            inbox.markRetryPending(clock.instant(), reason);
            return;
        }

        inbox.markFailed(clock.instant(), reason, SlackInteractivityFailureType.RETRY_EXHAUSTED);
    }
}

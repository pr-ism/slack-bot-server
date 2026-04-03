package com.slack.bot.application.interaction.box.in;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.slack.bot.application.interaction.BlockActionInteractionService;
import com.slack.bot.application.interaction.box.BoxFailureReasonTruncator;
import com.slack.bot.application.interaction.box.aop.BindInboxToOutboxSource;
import com.slack.bot.application.interaction.box.retry.InteractionRetryExceptionClassifier;
import com.slack.bot.application.interaction.view.ViewSubmissionInteractionCoordinator;
import com.slack.bot.global.config.properties.InteractionRetryProperties;
import com.slack.bot.infrastructure.interaction.box.SlackInteractionFailureType;
import com.slack.bot.infrastructure.interaction.box.in.SlackInteractionInbox;
import com.slack.bot.infrastructure.interaction.box.in.SlackInteractionInboxHistory;
import com.slack.bot.infrastructure.interaction.box.in.SlackInteractionInboxType;
import com.slack.bot.infrastructure.interaction.box.in.repository.SlackInteractionInboxRepository;
import java.time.Clock;
import java.util.function.Consumer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class SlackInteractionInboxEntryProcessor {

    private static final String UNKNOWN_FAILURE_REASON = "unknown failure";

    private final Clock clock;
    private final ObjectMapper objectMapper;
    private final RetryTemplate slackInteractionInboxRetryTemplate;
    private final BoxFailureReasonTruncator failureReasonTruncator;
    private final InteractionRetryProperties interactionRetryProperties;
    private final BlockActionInteractionService blockActionInteractionService;
    private final SlackInteractionInboxRepository slackInteractionInboxRepository;
    private final InteractionRetryExceptionClassifier retryExceptionClassifier;
    private final ViewSubmissionInteractionCoordinator viewSubmissionInteractionCoordinator;

    @BindInboxToOutboxSource
    @Transactional
    public void processClaimedBlockAction(Long inboxId) {
        processClaimedInbox(
                inboxId,
                payload -> blockActionInteractionService.handle(payload),
                SlackInteractionInboxType.BLOCK_ACTIONS
        );
    }

    @BindInboxToOutboxSource
    @Transactional
    public void processClaimedViewSubmission(Long inboxId) {
        processClaimedInbox(
                inboxId,
                payload -> viewSubmissionInteractionCoordinator.handleEnqueued(payload),
                SlackInteractionInboxType.VIEW_SUBMISSION
        );
    }

    private void processClaimedInbox(
            Long inboxId,
            Consumer<JsonNode> consumer,
            SlackInteractionInboxType interactionType
    ) {
        if (inboxId == null) {
            return;
        }

        slackInteractionInboxRepository.findById(inboxId)
                                       .ifPresentOrElse(
                                               claimedInbox -> processInTransaction(
                                                       claimedInbox,
                                                       consumer,
                                                       interactionType
                                               ),
                                               () -> log.warn(
                                                       "PROCESSING으로 전이된 inbox를 조회하지 못했습니다. inboxId={}",
                                                       inboxId
                                               )
                                       );
    }

    private void processInTransaction(
            SlackInteractionInbox claimedInbox,
            Consumer<JsonNode> consumer,
            SlackInteractionInboxType interactionType
    ) {
        SlackInteractionInboxHistory history;

        try {
            JsonNode payload = objectMapper.readTree(claimedInbox.getPayloadJson());

            slackInteractionInboxRetryTemplate.execute(context -> {
                consumer.accept(payload);
                return true;
            });

            history = claimedInbox.markProcessed(clock.instant());
        } catch (Exception e) {
            log.error(
                    "{} inbox 처리에 실패했습니다. inboxId={}",
                    interactionType,
                    claimedInbox.getId(),
                    e
            );
            history = markFailureStatus(claimedInbox, e);
        }

        slackInteractionInboxRepository.save(claimedInbox, history);
    }

    private SlackInteractionInboxHistory markFailureStatus(SlackInteractionInbox inbox, Exception e) {
        String reason = resolveFailureReason(e);

        if (!retryExceptionClassifier.isRetryable(e)) {
            return inbox.markFailed(clock.instant(), reason, SlackInteractionFailureType.BUSINESS_INVARIANT);
        }

        if (inbox.getProcessingAttempt() < interactionRetryProperties.inbox().maxAttempts()) {
            return inbox.markRetryPending(clock.instant(), reason);
        }

        return inbox.markFailed(clock.instant(), reason, SlackInteractionFailureType.RETRY_EXHAUSTED);
    }

    private String resolveFailureReason(Exception e) {
        String reason = failureReasonTruncator.truncate(e.getMessage());

        if (reason == null || reason.isBlank()) {
            return UNKNOWN_FAILURE_REASON;
        }

        return reason;
    }
}

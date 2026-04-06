package com.slack.bot.application.interaction.box.in;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.slack.bot.application.interaction.BlockActionInteractionService;
import com.slack.bot.application.interaction.box.BoxFailureReasonTruncator;
import com.slack.bot.application.interaction.box.aop.BindInboxToOutboxSource;
import com.slack.bot.application.interaction.box.in.exception.InboxProcessingLeaseLostException;
import com.slack.bot.application.interaction.box.retry.InteractionRetryExceptionClassifier;
import com.slack.bot.application.interaction.view.ViewSubmissionInteractionCoordinator;
import com.slack.bot.global.config.properties.InteractionRetryProperties;
import com.slack.bot.infrastructure.interaction.box.SlackInteractionFailureType;
import com.slack.bot.infrastructure.interaction.box.in.SlackInteractionInbox;
import com.slack.bot.infrastructure.interaction.box.in.SlackInteractionInboxHistory;
import com.slack.bot.infrastructure.interaction.box.in.SlackInteractionInboxType;
import com.slack.bot.infrastructure.interaction.box.in.repository.SlackInteractionInboxRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
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
    public void processClaimedBlockAction(Long inboxId, Instant claimedProcessingStartedAt) {
        processClaimedInbox(
                inboxId,
                claimedProcessingStartedAt,
                payload -> blockActionInteractionService.handle(payload),
                SlackInteractionInboxType.BLOCK_ACTIONS
        );
    }

    @BindInboxToOutboxSource
    @Transactional
    public void processClaimedViewSubmission(Long inboxId, Instant claimedProcessingStartedAt) {
        processClaimedInbox(
                inboxId,
                claimedProcessingStartedAt,
                payload -> viewSubmissionInteractionCoordinator.handleEnqueued(payload),
                SlackInteractionInboxType.VIEW_SUBMISSION
        );
    }

    private void processClaimedInbox(
            Long inboxId,
            Instant claimedProcessingStartedAt,
            Consumer<JsonNode> consumer,
            SlackInteractionInboxType interactionType
    ) {
        if (inboxId == null) {
            throw new IllegalArgumentException("inboxId는 비어 있을 수 없습니다.");
        }
        if (claimedProcessingStartedAt == null) {
            throw new IllegalArgumentException("claimedProcessingStartedAt은 비어 있을 수 없습니다.");
        }
        Instant normalizedClaimedProcessingStartedAt = normalizeProcessingStartedAt(claimedProcessingStartedAt);

        slackInteractionInboxRepository.findById(inboxId)
                                       .ifPresentOrElse(
                                               claimedInbox -> processInTransaction(
                                                       claimedInbox,
                                                       normalizedClaimedProcessingStartedAt,
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
            Instant claimedProcessingStartedAt,
            Consumer<JsonNode> consumer,
            SlackInteractionInboxType interactionType
    ) {
        if (!hasProcessingLease(claimedInbox, claimedProcessingStartedAt)) {
            logLeaseLost(claimedInbox.getId(), claimedProcessingStartedAt, claimedInbox);
            return;
        }

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

        persistWithLeaseCheck(claimedInbox, history, claimedProcessingStartedAt);
    }

    private void persistWithLeaseCheck(
            SlackInteractionInbox inbox,
            SlackInteractionInboxHistory history,
            Instant claimedProcessingStartedAt
    ) {
        boolean updated = slackInteractionInboxRepository.saveIfProcessingLeaseMatched(
                inbox,
                history,
                claimedProcessingStartedAt
        );
        if (updated) {
            return;
        }

        throw new InboxProcessingLeaseLostException(
                "slack interaction inbox processing lease를 상실했습니다. inboxId=" + inbox.getId()
        );
    }

    private SlackInteractionInboxHistory markFailureStatus(SlackInteractionInbox inbox, Exception e) {
        String reason = resolveFailureReason(e);
        return inbox.markFailure(
                clock.instant(),
                reason,
                retryExceptionClassifier.isRetryable(e),
                interactionRetryProperties.inbox().maxAttempts()
        );
    }

    private String resolveFailureReason(Exception e) {
        String reason = failureReasonTruncator.truncate(e.getMessage());

        if (reason == null || reason.isBlank()) {
            return UNKNOWN_FAILURE_REASON;
        }

        return reason;
    }

    private boolean hasProcessingLease(
            SlackInteractionInbox inbox,
            Instant claimedProcessingStartedAt
    ) {
        return inbox.hasClaimedProcessingLease(claimedProcessingStartedAt);
    }

    private void logLeaseLost(
            Long inboxId,
            Instant claimedProcessingStartedAt,
            SlackInteractionInbox inbox
    ) {
        if (inbox.hasClaimedProcessingLease()) {
            log.warn(
                    "slack interaction inbox 처리 lease를 상실해 처리를 건너뜁니다. inboxId={}, claimedProcessingStartedAt={}, actualProcessingLeaseClaimed=true, actualProcessingStartedAt={}",
                    inboxId,
                    claimedProcessingStartedAt,
                    inbox.currentProcessingLeaseStartedAt()
            );
            return;
        }

        log.warn(
                "slack interaction inbox 처리 lease를 상실해 처리를 건너뜁니다. inboxId={}, claimedProcessingStartedAt={}, actualProcessingLeaseClaimed=false",
                inboxId,
                claimedProcessingStartedAt
        );
    }

    private Instant normalizeProcessingStartedAt(Instant processingStartedAt) {
        return processingStartedAt.truncatedTo(ChronoUnit.MICROS);
    }
}

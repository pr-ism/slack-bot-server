package com.slack.bot.application.interaction.box.in;

import com.slack.bot.application.interaction.box.SlackInteractionIdempotencyKeyGenerator;
import com.slack.bot.application.interaction.box.SlackInteractionIdempotencyScope;
import com.slack.bot.application.interaction.box.aop.InteractionImmediateTriggerTarget;
import com.slack.bot.application.interaction.box.aop.TriggerInteractionImmediateProcessing;
import com.slack.bot.application.worker.PollingHintPublisher;
import com.slack.bot.application.worker.PollingHintTarget;
import com.slack.bot.global.config.properties.InteractionRetryProperties;
import com.slack.bot.global.config.properties.InteractionWorkerProperties;
import com.slack.bot.infrastructure.interaction.box.in.SlackInteractionInboxType;
import com.slack.bot.infrastructure.interaction.box.in.repository.SlackInteractionInboxRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.Set;
import java.util.function.BiConsumer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class SlackInteractionInboxProcessor {

    private static final String PROCESSING_TIMEOUT_FAILURE_REASON =
            "PROCESSING 타임아웃으로 복구 처리되었습니다.";

    private final Clock clock;
    private final InteractionRetryProperties interactionRetryProperties;
    private final InteractionWorkerProperties interactionWorkerProperties;
    private final SlackInteractionInboxRepository slackInteractionInboxRepository;
    private final SlackInteractionInboxIdempotencyPayloadEncoder idempotencyPayloadEncoder;
    private final SlackInteractionIdempotencyKeyGenerator idempotencyKeyGenerator;
    private final SlackInteractionInboxEntryProcessor slackInteractionInboxEntryProcessor;
    private final PollingHintPublisher pollingHintPublisher;

    @TriggerInteractionImmediateProcessing(
            value = InteractionImmediateTriggerTarget.BLOCK_ACTION_INBOX,
            onlyWhenEnqueued = true
    )
    public boolean enqueueBlockAction(String payloadJson) {
        String idempotencyPayload = idempotencyPayloadEncoder.encodeBlockAction(payloadJson);

        String idempotencyKey = idempotencyKeyGenerator.generate(
                SlackInteractionIdempotencyScope.BLOCK_ACTIONS,
                idempotencyPayload
        );

        return slackInteractionInboxRepository.enqueue(
                SlackInteractionInboxType.BLOCK_ACTIONS,
                idempotencyKey,
                payloadJson
        );
    }

    public int processPendingBlockActions(int limit) {
        return processPending(
                SlackInteractionInboxType.BLOCK_ACTIONS,
                limit,
                (inboxId, claimedProcessingStartedAt) -> slackInteractionInboxEntryProcessor.processClaimedBlockAction(
                        inboxId,
                        claimedProcessingStartedAt
                )
        );
    }

    @TriggerInteractionImmediateProcessing(
            value = InteractionImmediateTriggerTarget.VIEW_SUBMISSION_INBOX,
            onlyWhenEnqueued = true
    )
    public boolean enqueueViewSubmission(String payloadJson) {
        String idempotencyPayload = idempotencyPayloadEncoder.encodeViewSubmission(payloadJson);

        String idempotencyKey = idempotencyKeyGenerator.generate(
                SlackInteractionIdempotencyScope.VIEW_SUBMISSION,
                idempotencyPayload
        );

        return slackInteractionInboxRepository.enqueue(
                SlackInteractionInboxType.VIEW_SUBMISSION,
                idempotencyKey,
                payloadJson
        );
    }

    public int processPendingViewSubmissions(int limit) {
        return processPending(
                SlackInteractionInboxType.VIEW_SUBMISSION,
                limit,
                (inboxId, claimedProcessingStartedAt) -> slackInteractionInboxEntryProcessor.processClaimedViewSubmission(
                        inboxId,
                        claimedProcessingStartedAt
                )
        );
    }

    private int processPending(
            SlackInteractionInboxType interactionType,
            int limit,
            BiConsumer<Long, Instant> action
    ) {
        Set<Long> claimedInboxIds = new HashSet<>();
        int claimedCount = 0;
        for (int count = 0; count < limit; count++) {
            ClaimStep nextClaimStep = processNextClaimedInbox(
                    interactionType,
                    action,
                    claimedInboxIds,
                    claimedCount
            );
            if (nextClaimStep.isUnchangedFrom(claimedCount)) {
                return claimedCount;
            }

            claimedInboxIds = nextClaimStep.claimedInboxIds();
            claimedCount = nextClaimStep.claimedCount();
        }

        return claimedCount;
    }

    private ClaimStep processNextClaimedInbox(
            SlackInteractionInboxType interactionType,
            BiConsumer<Long, Instant> action,
            Set<Long> claimedInboxIds,
            int claimedCount
    ) {
        Instant claimedProcessingStartedAt = normalizeProcessingStartedAt(clock.instant());
        return slackInteractionInboxRepository.claimNextId(
                interactionType,
                claimedProcessingStartedAt,
                claimedInboxIds
        )
                .map(inboxId -> createClaimStep(
                        inboxId,
                        claimedProcessingStartedAt,
                        action,
                        interactionType,
                        claimedInboxIds,
                        claimedCount
                ))
                .orElseGet(() -> new ClaimStep(claimedInboxIds, claimedCount));
    }

    public int recoverBlockActionTimeoutProcessing() {
        return recoverTimeoutProcessing(
                SlackInteractionInboxType.BLOCK_ACTIONS,
                interactionWorkerProperties.inbox().blockActions().processingTimeoutMs()
        );
    }

    private Instant normalizeProcessingStartedAt(Instant processingStartedAt) {
        return processingStartedAt.truncatedTo(ChronoUnit.MICROS);
    }

    private ClaimStep createClaimStep(
            Long inboxId,
            Instant claimedProcessingStartedAt,
            BiConsumer<Long, Instant> action,
            SlackInteractionInboxType interactionType,
            Set<Long> claimedInboxIds,
            int claimedCount
    ) {
        Set<Long> nextClaimedInboxIds = new HashSet<>(claimedInboxIds);
        nextClaimedInboxIds.add(inboxId);
        processSafely(inboxId, claimedProcessingStartedAt, action, interactionType);
        return new ClaimStep(nextClaimedInboxIds, claimedCount + 1);
    }

    public int recoverViewSubmissionTimeoutProcessing() {
        return recoverTimeoutProcessing(
                SlackInteractionInboxType.VIEW_SUBMISSION,
                interactionWorkerProperties.inbox().viewSubmission().processingTimeoutMs()
        );
    }

    private int recoverTimeoutProcessing(SlackInteractionInboxType interactionType, long processingTimeoutMs) {
        Instant now = clock.instant();
        int recoveredCount = slackInteractionInboxRepository.recoverTimeoutProcessing(
                interactionType,
                now.minusMillis(processingTimeoutMs),
                now,
                PROCESSING_TIMEOUT_FAILURE_REASON,
                interactionRetryProperties.inbox().maxAttempts(),
                resolveTimeoutRecoveryBatchSize(interactionType)
        );

        if (recoveredCount > 0) {
            pollingHintPublisher.publish(resolvePollingHintTarget(interactionType));
            log.warn("{} inbox PROCESSING 고착 건을 복구했습니다. count={}", interactionType, recoveredCount);
        }

        return recoveredCount;
    }

    private int resolveTimeoutRecoveryBatchSize(SlackInteractionInboxType interactionType) {
        if (interactionType.isBlockActions()) {
            return interactionWorkerProperties.inbox().blockActions().timeoutRecoveryBatchSize();
        }

        return interactionWorkerProperties.inbox().viewSubmission().timeoutRecoveryBatchSize();
    }

    private PollingHintTarget resolvePollingHintTarget(SlackInteractionInboxType interactionType) {
        if (interactionType.isBlockActions()) {
            return PollingHintTarget.BLOCK_ACTION_INBOX;
        }

        return PollingHintTarget.VIEW_SUBMISSION_INBOX;
    }

    private void processSafely(
            Long inboxId,
            Instant claimedProcessingStartedAt,
            BiConsumer<Long, Instant> action,
            SlackInteractionInboxType interactionType
    ) {
        try {
            action.accept(inboxId, claimedProcessingStartedAt);
        } catch (Exception e) {
            log.error(
                    "{} inbox 엔트리 처리 중 예상치 못한 오류가 발생했습니다. inboxId={}",
                    interactionType,
                    inboxId,
                    e
            );
        }
    }

    private record ClaimStep(
            Set<Long> claimedInboxIds,
            int claimedCount
    ) {
        private boolean isUnchangedFrom(int claimedCount) {
            return this.claimedCount == claimedCount;
        }
    }
}

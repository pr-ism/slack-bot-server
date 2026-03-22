package com.slack.bot.application.interaction.box.in;

import com.slack.bot.application.interaction.box.SlackInteractionIdempotencyKeyGenerator;
import com.slack.bot.application.interaction.box.SlackInteractionIdempotencyScope;
import com.slack.bot.application.interaction.box.aop.InteractionImmediateTriggerTarget;
import com.slack.bot.application.interaction.box.aop.TriggerInteractionImmediateProcessing;
import com.slack.bot.global.config.properties.InteractionRetryProperties;
import com.slack.bot.global.config.properties.InteractionWorkerProperties;
import com.slack.bot.infrastructure.interaction.box.in.SlackInteractionInbox;
import com.slack.bot.infrastructure.interaction.box.in.SlackInteractionInboxType;
import com.slack.bot.infrastructure.interaction.box.in.repository.SlackInteractionInboxRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.function.Consumer;
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

    public void processPendingBlockActions(int limit) {
        processPending(
                SlackInteractionInboxType.BLOCK_ACTIONS,
                limit,
                pending -> slackInteractionInboxEntryProcessor.processBlockAction(pending)
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

    public void processPendingViewSubmissions(int limit) {
        processPending(
                SlackInteractionInboxType.VIEW_SUBMISSION,
                limit,
                pending -> slackInteractionInboxEntryProcessor.processViewSubmission(pending)
        );
    }

    private void processPending(
            SlackInteractionInboxType interactionType,
            int limit,
            Consumer<SlackInteractionInbox> action
    ) {
        List<SlackInteractionInbox> pendings = slackInteractionInboxRepository.findClaimable(
                interactionType,
                limit
        );

        for (SlackInteractionInbox pending : pendings) {
            processSafely(pending, action, interactionType);
        }
    }

    public int recoverBlockActionTimeoutProcessing() {
        return recoverTimeoutProcessing(
                SlackInteractionInboxType.BLOCK_ACTIONS,
                interactionWorkerProperties.inbox().blockActions().processingTimeoutMs()
        );
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
                interactionRetryProperties.inbox().maxAttempts()
        );

        if (recoveredCount > 0) {
            log.warn("{} inbox PROCESSING 고착 건을 복구했습니다. count={}", interactionType, recoveredCount);
        }

        return recoveredCount;
    }

    private void processSafely(
            SlackInteractionInbox pending,
            Consumer<SlackInteractionInbox> action,
            SlackInteractionInboxType interactionType
    ) {
        try {
            action.accept(pending);
        } catch (Exception e) {
            log.error(
                    "{} inbox 엔트리 처리 중 예상치 못한 오류가 발생했습니다. inboxId={}",
                    interactionType,
                    pending.getId(),
                    e
            );
        }
    }
}

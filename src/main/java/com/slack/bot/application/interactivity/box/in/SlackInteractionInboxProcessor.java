package com.slack.bot.application.interactivity.box.in;

import com.slack.bot.application.interactivity.box.SlackInteractionIdempotencyKeyGenerator;
import com.slack.bot.application.interactivity.box.SlackInteractionIdempotencyScope;
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
            "PROCESSING 타임아웃으로 재시도 대기 상태로 복구되었습니다.";

    private final Clock clock;
    private final InteractionWorkerProperties interactionWorkerProperties;
    private final SlackInteractionInboxRepository slackInteractionInboxRepository;
    private final SlackInteractionIdempotencyKeyGenerator idempotencyKeyGenerator;
    private final SlackInteractionInboxEntryProcessor slackInteractionInboxEntryProcessor;

    public boolean enqueueBlockAction(String payloadJson) {
        String idempotencyKey = idempotencyKeyGenerator.generate(
                SlackInteractionIdempotencyScope.BLOCK_ACTIONS,
                payloadJson
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
                interactionWorkerProperties.inbox().blockActions().processingTimeoutMs(),
                limit,
                pending -> slackInteractionInboxEntryProcessor.processBlockAction(pending)
        );
    }

    public boolean enqueueViewSubmission(String payloadJson) {
        String idempotencyKey = idempotencyKeyGenerator.generate(
                SlackInteractionIdempotencyScope.VIEW_SUBMISSION,
                payloadJson
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
                interactionWorkerProperties.inbox().viewSubmission().processingTimeoutMs(),
                limit,
                pending -> slackInteractionInboxEntryProcessor.processViewSubmission(pending)
        );
    }

    private void processPending(
            SlackInteractionInboxType interactionType,
            long processingTimeoutMs,
            int limit,
            Consumer<SlackInteractionInbox> action
    ) {
        recoverTimeoutProcessing(interactionType, processingTimeoutMs);

        List<SlackInteractionInbox> pendings = slackInteractionInboxRepository.findClaimable(
                interactionType,
                limit
        );

        for (SlackInteractionInbox pending : pendings) {
            processSafely(pending, action, interactionType);
        }
    }

    private void recoverTimeoutProcessing(SlackInteractionInboxType interactionType, long processingTimeoutMs) {
        Instant now = clock.instant();
        int recoveredCount = slackInteractionInboxRepository.recoverTimeoutProcessing(
                interactionType,
                now.minusMillis(processingTimeoutMs),
                now,
                PROCESSING_TIMEOUT_FAILURE_REASON
        );

        if (recoveredCount > 0) {
            log.warn("{} inbox PROCESSING 고착 건을 복구했습니다. count={}", interactionType, recoveredCount);
        }
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

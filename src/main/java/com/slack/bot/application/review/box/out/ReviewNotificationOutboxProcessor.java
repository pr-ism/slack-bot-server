package com.slack.bot.application.review.box.out;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.slack.bot.application.interactivity.box.BoxFailureReasonTruncator;
import com.slack.bot.application.interactivity.box.retry.InteractionRetryExceptionClassifier;
import com.slack.bot.domain.workspace.Workspace;
import com.slack.bot.domain.workspace.repository.WorkspaceRepository;
import com.slack.bot.global.config.properties.InteractionRetryProperties;
import com.slack.bot.infrastructure.interaction.box.SlackInteractivityFailureType;
import com.slack.bot.infrastructure.interaction.client.NotificationTransportApiClient;
import com.slack.bot.infrastructure.review.box.out.ReviewNotificationOutbox;
import com.slack.bot.infrastructure.review.box.out.repository.ReviewNotificationOutboxRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ReviewNotificationOutboxProcessor {

    private static final String PROCESSING_TIMEOUT_FAILURE_REASON =
            "PROCESSING 타임아웃으로 복구 처리되었습니다.";
    private static final String UNKNOWN_FAILURE_REASON = "unknown failure";

    private final Clock clock;
    private final ObjectMapper objectMapper;
    private final RetryTemplate slackNotificationOutboxRetryTemplate;
    private final WorkspaceRepository workspaceRepository;
    private final BoxFailureReasonTruncator failureReasonTruncator;
    private final InteractionRetryProperties interactionRetryProperties;
    private final NotificationTransportApiClient notificationTransportApiClient;
    private final ReviewNotificationOutboxRepository reviewNotificationOutboxRepository;
    private final InteractionRetryExceptionClassifier retryExceptionClassifier;

    public void processPending(int limit, long processingTimeoutMs) {
        validateProcessingTimeoutMs(processingTimeoutMs);

        recoverTimeoutProcessing(processingTimeoutMs);

        List<ReviewNotificationOutbox> pendings = reviewNotificationOutboxRepository.findClaimable(limit);

        for (ReviewNotificationOutbox pending : pendings) {
            processSafely(pending);
        }
    }

    private void recoverTimeoutProcessing(long processingTimeoutMs) {
        Instant now = clock.instant();
        int recoveredCount = reviewNotificationOutboxRepository.recoverTimeoutProcessing(
                now.minusMillis(processingTimeoutMs),
                now,
                PROCESSING_TIMEOUT_FAILURE_REASON,
                interactionRetryProperties.outbox().maxAttempts()
        );

        if (recoveredCount > 0) {
            log.warn("review_notification outbox PROCESSING 고착 건을 복구했습니다. count={}", recoveredCount);
        }
    }

    private void processSafely(ReviewNotificationOutbox pending) {
        Long outboxId = pending.getId();
        if (outboxId == null) {
            return;
        }

        if (!reviewNotificationOutboxRepository.markProcessingIfClaimable(outboxId, clock.instant())) {
            return;
        }

        reviewNotificationOutboxRepository.findById(outboxId)
                                         .ifPresentOrElse(
                                                 this::processClaimedOutbox,
                                                 () -> log.warn(
                                                         "PROCESSING으로 전이된 review_notification outbox를 조회하지 못했습니다. outboxId={}",
                                                         outboxId
                                                 )
                                         );
    }

    private void processClaimedOutbox(ReviewNotificationOutbox outbox) {
        try {
            slackNotificationOutboxRetryTemplate.execute(context -> {
                dispatch(outbox);
                return null;
            });

            outbox.markSent(clock.instant());
            reviewNotificationOutboxRepository.save(outbox);
        } catch (Exception exception) {
            log.warn("review_notification outbox 처리에 실패했습니다. outboxId={}", outbox.getId(), exception);

            markFailureStatus(outbox, exception);
            reviewNotificationOutboxRepository.save(outbox);
        }
    }

    private void dispatch(ReviewNotificationOutbox outbox) throws JsonProcessingException {
        String token = resolveToken(outbox.getTeamId());

        notificationTransportApiClient.sendBlockMessage(
                token,
                outbox.getChannelId(),
                readBlocks(outbox.getBlocksJson()),
                outbox.getFallbackText()
        );
    }

    private String resolveToken(String teamId) {
        Workspace workspace = workspaceRepository.findByTeamId(teamId)
                                                 .orElseThrow(() -> new IllegalStateException(
                                                         "teamId에 해당하는 워크스페이스를 찾을 수 없습니다. teamId=" + teamId
                                                 ));

        return workspace.getAccessToken();
    }

    private JsonNode readBlocks(String blocksJson) throws JsonProcessingException {
        return objectMapper.readTree(blocksJson);
    }

    private void markFailureStatus(ReviewNotificationOutbox outbox, Exception exception) {
        String reason = resolveFailureReason(exception);

        if (!retryExceptionClassifier.isRetryable(exception)) {
            outbox.markFailed(clock.instant(), reason, SlackInteractivityFailureType.BUSINESS_INVARIANT);
            return;
        }

        if (outbox.getProcessingAttempt() < interactionRetryProperties.outbox().maxAttempts()) {
            outbox.markRetryPending(clock.instant(), reason);
            return;
        }

        outbox.markFailed(clock.instant(), reason, SlackInteractivityFailureType.RETRY_EXHAUSTED);
    }

    private String resolveFailureReason(Exception exception) {
        String reason = failureReasonTruncator.truncate(exception.getMessage());

        if (reason == null || reason.isBlank()) {
            return UNKNOWN_FAILURE_REASON;
        }

        return reason;
    }

    private void validateProcessingTimeoutMs(long processingTimeoutMs) {
        if (processingTimeoutMs <= 0) {
            throw new IllegalArgumentException("processingTimeoutMs는 0보다 커야 합니다.");
        }
    }
}

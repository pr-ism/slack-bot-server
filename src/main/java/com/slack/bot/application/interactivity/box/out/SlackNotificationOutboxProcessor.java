package com.slack.bot.application.interactivity.box.out;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.slack.bot.application.interactivity.box.BoxFailureReasonTruncator;
import com.slack.bot.application.interactivity.box.out.exception.OutboxWorkspaceNotFoundException;
import com.slack.bot.application.interactivity.box.out.exception.UnsupportedSlackNotificationOutboxMessageTypeException;
import com.slack.bot.application.interactivity.box.retry.InteractionRetryExceptionClassifier;
import com.slack.bot.domain.workspace.Workspace;
import com.slack.bot.domain.workspace.repository.WorkspaceRepository;
import com.slack.bot.global.config.properties.InteractionRetryProperties;
import com.slack.bot.global.config.properties.InteractionWorkerProperties;
import com.slack.bot.infrastructure.interaction.box.SlackInteractivityFailureType;
import com.slack.bot.infrastructure.interaction.box.out.SlackNotificationOutbox;
import com.slack.bot.infrastructure.interaction.box.out.SlackNotificationOutboxMessageType;
import com.slack.bot.infrastructure.interaction.box.out.repository.SlackNotificationOutboxRepository;
import com.slack.bot.infrastructure.interaction.client.NotificationTransportApiClient;
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
public class SlackNotificationOutboxProcessor {

    private static final String PROCESSING_TIMEOUT_FAILURE_REASON = "PROCESSING 타임아웃으로 재시도 대기 상태로 복구되었습니다.";
    private static final String UNKNOWN_FAILURE_REASON = "unknown failure";

    private final Clock clock;
    private final ObjectMapper objectMapper;
    private final RetryTemplate slackNotificationOutboxRetryTemplate;
    private final WorkspaceRepository workspaceRepository;
    private final BoxFailureReasonTruncator failureReasonTruncator;
    private final InteractionRetryProperties interactionRetryProperties;
    private final InteractionWorkerProperties interactionWorkerProperties;
    private final NotificationTransportApiClient notificationTransportApiClient;
    private final SlackNotificationOutboxRepository slackNotificationOutboxRepository;
    private final InteractionRetryExceptionClassifier retryExceptionClassifier;

    public void processPending(int limit) {
        recoverTimeoutProcessing();

        List<SlackNotificationOutbox> pendings = slackNotificationOutboxRepository.findClaimable(limit);

        for (SlackNotificationOutbox pending : pendings) {
            processPending(pending);
        }
    }

    private void recoverTimeoutProcessing() {
        Instant now = clock.instant();
        int recoveredCount = slackNotificationOutboxRepository.recoverTimeoutProcessing(
                now.minusMillis(interactionWorkerProperties.outbox().processingTimeoutMs()),
                now,
                PROCESSING_TIMEOUT_FAILURE_REASON
        );

        if (recoveredCount > 0) {
            log.warn("outbox PROCESSING 고착 건을 복구했습니다. count={}", recoveredCount);
        }
    }

    private void processPending(SlackNotificationOutbox pending) {
        Long outboxId = pending.getId();
        if (outboxId == null) {
            return;
        }

        if (!slackNotificationOutboxRepository.markProcessingIfClaimable(outboxId, clock.instant())) {
            return;
        }

        slackNotificationOutboxRepository.findById(outboxId)
                                         .ifPresentOrElse(
                                                 outbox -> processClaimedOutbox(outbox),
                                                 () -> logMissingOutbox(outboxId)
                                         );
    }

    private void processClaimedOutbox(SlackNotificationOutbox outbox) {
        try {
            slackNotificationOutboxRetryTemplate.execute(context -> {
                dispatch(outbox);
                return null;
            });
            outbox.markSent(clock.instant());
            slackNotificationOutboxRepository.save(outbox);
        } catch (Exception e) {
            log.warn(
                    "슬랙 알림 outbox 처리에 실패했습니다. outboxId={}",
                    outbox.getId(),
                    e
            );
            markFailureStatus(outbox, e);
            slackNotificationOutboxRepository.save(outbox);
        }
    }

    private void logMissingOutbox(Long outboxId) {
        log.warn(
                "PROCESSING으로 전이된 outbox를 조회하지 못했습니다. outboxId={}",
                outboxId
        );
    }

    private void dispatch(SlackNotificationOutbox outbox) throws JsonProcessingException {
        SlackNotificationOutboxMessageType messageType = outbox.getMessageType();
        String token = resolveToken(outbox.getTeamId());

        if (messageType == SlackNotificationOutboxMessageType.EPHEMERAL_TEXT) {
            notificationTransportApiClient.sendEphemeralMessage(
                    token,
                    outbox.getChannelId(),
                    outbox.getUserId(),
                    outbox.getText()
            );
            return;
        }
        if (messageType == SlackNotificationOutboxMessageType.EPHEMERAL_BLOCKS) {
            notificationTransportApiClient.sendEphemeralBlockMessage(
                    token,
                    outbox.getChannelId(),
                    outbox.getUserId(),
                    readBlocks(outbox.getBlocksJson()),
                    outbox.getFallbackText()
            );
            return;
        }
        if (messageType == SlackNotificationOutboxMessageType.CHANNEL_TEXT) {
            notificationTransportApiClient.sendMessage(token, outbox.getChannelId(), outbox.getText());
            return;
        }
        if (messageType == SlackNotificationOutboxMessageType.CHANNEL_BLOCKS) {
            notificationTransportApiClient.sendBlockMessage(
                    token,
                    outbox.getChannelId(),
                    readBlocks(outbox.getBlocksJson()),
                    outbox.getFallbackText()
            );
            return;
        }

        throw new UnsupportedSlackNotificationOutboxMessageTypeException(messageType);
    }

    private JsonNode readBlocks(String blocksJson) throws JsonProcessingException {
        return objectMapper.readTree(blocksJson);
    }

    private String resolveToken(String teamId) {
        Workspace workspace = workspaceRepository.findByTeamId(teamId)
                                                 .orElseThrow(() -> OutboxWorkspaceNotFoundException.forTeamId(teamId));

        return workspace.getAccessToken();
    }

    private void markFailureStatus(SlackNotificationOutbox outbox, Exception exception) {
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
}

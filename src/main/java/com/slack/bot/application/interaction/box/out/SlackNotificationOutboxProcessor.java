package com.slack.bot.application.interaction.box.out;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.slack.bot.application.interaction.box.BoxFailureReasonTruncator;
import com.slack.bot.application.interaction.box.out.exception.OutboxProcessingLeaseLostException;
import com.slack.bot.application.interaction.box.out.exception.OutboxWorkspaceNotFoundException;
import com.slack.bot.application.interaction.box.out.exception.UnsupportedSlackNotificationOutboxMessageTypeException;
import com.slack.bot.application.interaction.box.retry.InteractionRetryExceptionClassifier;
import com.slack.bot.domain.workspace.Workspace;
import com.slack.bot.domain.workspace.repository.WorkspaceRepository;
import com.slack.bot.global.config.properties.InteractionRetryProperties;
import com.slack.bot.global.config.properties.InteractionWorkerProperties;
import com.slack.bot.infrastructure.interaction.box.SlackInteractionFailureType;
import com.slack.bot.infrastructure.interaction.box.out.SlackNotificationOutbox;
import com.slack.bot.infrastructure.interaction.box.out.SlackNotificationOutboxMessageType;
import com.slack.bot.infrastructure.interaction.box.out.repository.SlackNotificationOutboxRepository;
import com.slack.bot.infrastructure.interaction.client.NotificationTransportApiClient;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class SlackNotificationOutboxProcessor {

    private static final String PROCESSING_TIMEOUT_FAILURE_REASON = "PROCESSING 타임아웃으로 복구 처리되었습니다.";
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

    public int processPending(int limit) {
        Set<Long> claimedOutboxIds = new HashSet<>();
        int claimedCount = 0;
        for (int count = 0; count < limit; count++) {
            Instant claimedProcessingStartedAt = currentLeaseStartedAt();
            Long claimedOutboxId = slackNotificationOutboxRepository.claimNextId(
                    claimedProcessingStartedAt,
                    claimedOutboxIds
            ).orElse(null);
            if (claimedOutboxId == null) {
                return claimedCount;
            }

            claimedOutboxIds.add(claimedOutboxId);
            claimedCount++;
            processClaimedOutboxSafely(claimedOutboxId, claimedProcessingStartedAt);
        }

        return claimedCount;
    }

    public int recoverTimeoutProcessing() {
        Instant now = clock.instant();
        int recoveredCount = slackNotificationOutboxRepository.recoverTimeoutProcessing(
                now.minusMillis(interactionWorkerProperties.outbox().processingTimeoutMs()),
                now,
                PROCESSING_TIMEOUT_FAILURE_REASON,
                interactionRetryProperties.outbox().maxAttempts()
        );

        if (recoveredCount > 0) {
            log.warn("outbox PROCESSING 고착 건을 복구했습니다. count={}", recoveredCount);
        }

        return recoveredCount;
    }

    private void processClaimedOutboxSafely(Long outboxId, Instant claimedProcessingStartedAt) {
        slackNotificationOutboxRepository.findById(outboxId)
                                         .ifPresentOrElse(
                                                 outbox -> processClaimedOutbox(outbox, claimedProcessingStartedAt),
                                                 () -> logMissingOutbox(outboxId)
                                         );
    }

    private void processClaimedOutbox(
            SlackNotificationOutbox outbox,
            Instant claimedProcessingStartedAt
    ) {
        if (!hasProcessingLease(outbox, claimedProcessingStartedAt)) {
            logLeaseLost(outbox.getId(), claimedProcessingStartedAt, outbox.getProcessingStartedAt());
            return;
        }

        AtomicReference<Instant> currentProcessingStartedAt = new AtomicReference<>(claimedProcessingStartedAt);
        try {
            slackNotificationOutboxRetryTemplate.execute(context -> {
                renewProcessingLease(outbox, currentProcessingStartedAt);
                dispatch(outbox);
                return null;
            });
        } catch (OutboxProcessingLeaseLostException e) {
            return;
        } catch (Exception e) {
            log.warn(
                    "슬랙 알림 outbox 처리에 실패했습니다. outboxId={}",
                    outbox.getId(),
                    e
            );
            markFailureStatus(outbox, e);
            persistFailureStatus(outbox, currentProcessingStartedAt.get());
            return;
        }

        outbox.markSent(clock.instant());
        persistSentStatus(outbox, currentProcessingStartedAt.get());
    }

    private void persistSentStatus(
            SlackNotificationOutbox outbox,
            Instant claimedProcessingStartedAt
    ) {
        try {
            boolean updated = slackNotificationOutboxRepository.saveIfProcessingLeaseMatched(
                    outbox,
                    claimedProcessingStartedAt
            );
            if (updated) {
                return;
            }
        } catch (Exception e) {
            log.error(
                    "슬랙 알림 outbox 전송은 성공했지만 최종 상태 저장에 실패했습니다. outboxId={}",
                    outbox.getId(),
                    e
            );
            return;
        }

        logLeaseLost(outbox.getId(), claimedProcessingStartedAt, outbox.getProcessingStartedAt());
    }

    private void persistFailureStatus(
            SlackNotificationOutbox outbox,
            Instant claimedProcessingStartedAt
    ) {
        try {
            boolean updated = slackNotificationOutboxRepository.saveIfProcessingLeaseMatched(
                    outbox,
                    claimedProcessingStartedAt
            );
            if (updated) {
                return;
            }
        } catch (Exception e) {
            log.error(
                    "슬랙 알림 outbox 실패 상태 저장에 실패했습니다. outboxId={}",
                    outbox.getId(),
                    e
            );
            return;
        }

        logLeaseLost(outbox.getId(), claimedProcessingStartedAt, outbox.getProcessingStartedAt());
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
            outbox.markFailed(clock.instant(), reason, SlackInteractionFailureType.BUSINESS_INVARIANT);
            return;
        }

        if (outbox.getProcessingAttempt() < interactionRetryProperties.outbox().maxAttempts()) {
            outbox.markRetryPending(clock.instant(), reason);
            return;
        }

        outbox.markFailed(clock.instant(), reason, SlackInteractionFailureType.RETRY_EXHAUSTED);
    }

    private String resolveFailureReason(Exception exception) {
        String reason = failureReasonTruncator.truncate(exception.getMessage());

        if (reason == null || reason.isBlank()) {
            return UNKNOWN_FAILURE_REASON;
        }

        return reason;
    }

    private Instant currentLeaseStartedAt() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }

    private void renewProcessingLease(
            SlackNotificationOutbox outbox,
            AtomicReference<Instant> currentProcessingStartedAt
    ) {
        Instant renewedProcessingStartedAt = currentLeaseStartedAt();
        boolean renewed = slackNotificationOutboxRepository.renewProcessingLease(
                outbox.getId(),
                currentProcessingStartedAt.get(),
                renewedProcessingStartedAt
        );
        if (!renewed) {
            logLeaseLost(outbox.getId(), currentProcessingStartedAt.get(), renewedProcessingStartedAt);
            throw new OutboxProcessingLeaseLostException("outbox processing lease를 상실했습니다.");
        }

        currentProcessingStartedAt.set(renewedProcessingStartedAt);
        outbox.renewProcessingLease(renewedProcessingStartedAt);
    }

    private boolean hasProcessingLease(
            SlackNotificationOutbox outbox,
            Instant claimedProcessingStartedAt
    ) {
        Instant actualProcessingStartedAt = outbox.getProcessingStartedAt();

        return claimedProcessingStartedAt.equals(actualProcessingStartedAt);
    }

    private void logLeaseLost(
            Long outboxId,
            Instant claimedProcessingStartedAt,
            Instant actualProcessingStartedAt
    ) {
        log.warn(
                "outbox 처리 lease를 상실해 최종 상태 저장을 건너뜁니다. outboxId={}, claimedProcessingStartedAt={}, actualProcessingStartedAt={}",
                outboxId,
                claimedProcessingStartedAt,
                actualProcessingStartedAt
        );
    }
}

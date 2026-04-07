package com.slack.bot.application.interaction.box.out;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.slack.bot.application.interaction.box.BoxFailureReasonTruncator;
import com.slack.bot.application.interaction.box.out.exception.OutboxProcessingLeaseLostException;
import com.slack.bot.application.interaction.box.out.exception.OutboxWorkspaceNotFoundException;
import com.slack.bot.application.interaction.box.out.exception.UnsupportedSlackNotificationOutboxMessageTypeException;
import com.slack.bot.application.interaction.box.retry.InteractionRetryExceptionClassifier;
import com.slack.bot.application.worker.PollingHintPublisher;
import com.slack.bot.application.worker.PollingHintTarget;
import com.slack.bot.domain.workspace.Workspace;
import com.slack.bot.domain.workspace.repository.WorkspaceRepository;
import com.slack.bot.global.config.properties.InteractionRetryProperties;
import com.slack.bot.global.config.properties.InteractionWorkerProperties;
import com.slack.bot.infrastructure.common.BoxProcessingLease;
import com.slack.bot.infrastructure.interaction.box.SlackInteractionFailureType;
import com.slack.bot.infrastructure.interaction.box.out.SlackNotificationOutbox;
import com.slack.bot.infrastructure.interaction.box.out.SlackNotificationOutboxHistory;
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
    private final PollingHintPublisher pollingHintPublisher;
    private final NotificationTransportApiClient notificationTransportApiClient;
    private final SlackNotificationOutboxRepository slackNotificationOutboxRepository;
    private final InteractionRetryExceptionClassifier retryExceptionClassifier;

    public int processPending(int limit) {
        Set<Long> claimedOutboxIds = new HashSet<>();
        int claimedCount = 0;
        for (int count = 0; count < limit; count++) {
            Instant claimedProcessingStartedAt = currentLeaseStartedAt();
            int nextClaimedCount = processNextClaimedOutbox(
                    claimedOutboxIds,
                    claimedCount,
                    claimedProcessingStartedAt
            );
            if (nextClaimedCount == claimedCount) {
                return claimedCount;
            }

            claimedCount = nextClaimedCount;
        }

        return claimedCount;
    }

    private int processNextClaimedOutbox(
            Set<Long> claimedOutboxIds,
            int claimedCount,
            Instant claimedProcessingStartedAt
    ) {
        return slackNotificationOutboxRepository.claimNextId(
                    claimedProcessingStartedAt,
                    claimedOutboxIds
            )
            .map(claimedOutboxId -> {
                claimedOutboxIds.add(claimedOutboxId);
                processClaimedOutboxSafely(claimedOutboxId, claimedProcessingStartedAt);
                return claimedCount + 1;
            })
            .orElse(claimedCount);
    }

    public int recoverTimeoutProcessing() {
        Instant now = clock.instant();
        int recoveredCount = slackNotificationOutboxRepository.recoverTimeoutProcessing(
                now.minusMillis(interactionWorkerProperties.outbox().processingTimeoutMs()),
                now,
                PROCESSING_TIMEOUT_FAILURE_REASON,
                interactionRetryProperties.outbox().maxAttempts(),
                interactionWorkerProperties.outbox().timeoutRecoveryBatchSize()
        );

        if (recoveredCount > 0) {
            pollingHintPublisher.publish(PollingHintTarget.INTERACTION_OUTBOX);
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
            logLeaseLost(outbox.getId(), claimedProcessingStartedAt, outbox.getProcessingLease());
            return;
        }

        AtomicReference<Instant> currentProcessingStartedAt = new AtomicReference<>(claimedProcessingStartedAt);
        try {
            slackNotificationOutboxRetryTemplate.execute(context -> {
                renewProcessingLease(outbox, currentProcessingStartedAt);
                dispatch(outbox);
                return true;
            });
        } catch (OutboxProcessingLeaseLostException e) {
            return;
        } catch (Exception e) {
            log.warn(
                    "슬랙 알림 outbox 처리에 실패했습니다. outboxId={}",
                    outbox.getId(),
                    e
            );
            SlackNotificationOutboxHistory history = markFailureStatus(outbox, e);
            persistFailureStatus(outbox, history, currentProcessingStartedAt.get());
            return;
        }

        SlackNotificationOutboxHistory history = outbox.markSent(clock.instant());
        persistSentStatus(outbox, history, currentProcessingStartedAt.get());
    }

    private void persistSentStatus(
            SlackNotificationOutbox outbox,
            SlackNotificationOutboxHistory history,
            Instant claimedProcessingStartedAt
    ) {
        try {
            boolean updated = slackNotificationOutboxRepository.saveIfProcessingLeaseMatched(
                    outbox,
                    history,
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

        logPersistLeaseLost(outbox.getId(), claimedProcessingStartedAt);
    }

    private void persistFailureStatus(
            SlackNotificationOutbox outbox,
            SlackNotificationOutboxHistory history,
            Instant claimedProcessingStartedAt
    ) {
        try {
            boolean updated = slackNotificationOutboxRepository.saveIfProcessingLeaseMatched(
                    outbox,
                    history,
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

        logPersistLeaseLost(outbox.getId(), claimedProcessingStartedAt);
    }

    private void logMissingOutbox(Long outboxId) {
        log.warn(
                "PROCESSING으로 전이된 outbox를 조회하지 못했습니다. outboxId={}",
                outboxId
        );
    }

    private void dispatch(SlackNotificationOutbox outbox) throws JsonProcessingException {
        SlackNotificationOutboxMessageType messageType = outbox.getMessageType();
        if (messageType == null) {
            throw new UnsupportedSlackNotificationOutboxMessageTypeException(null);
        }
        String token = resolveToken(outbox.getTeamId());
        messageType.dispatch(new SlackNotificationOutboxMessageType.Dispatcher() {
            @Override
            public void dispatchEphemeralText() {
                notificationTransportApiClient.sendEphemeralMessage(
                        token,
                        outbox.getChannelId(),
                        outbox.requiredUserId(),
                        outbox.requiredText()
                );
            }

            @Override
            public void dispatchEphemeralBlocks() throws JsonProcessingException {
                notificationTransportApiClient.sendEphemeralBlockMessage(
                        token,
                        outbox.getChannelId(),
                        outbox.requiredUserId(),
                        readBlocks(outbox.requiredBlocksJson()),
                        outbox.fallbackTextOrBlank()
                );
            }

            @Override
            public void dispatchChannelText() {
                notificationTransportApiClient.sendMessage(
                        token,
                        outbox.getChannelId(),
                        outbox.requiredText()
                );
            }

            @Override
            public void dispatchChannelBlocks() throws JsonProcessingException {
                notificationTransportApiClient.sendBlockMessage(
                        token,
                        outbox.getChannelId(),
                        readBlocks(outbox.requiredBlocksJson()),
                        outbox.fallbackTextOrBlank()
                );
            }
        });
    }

    private JsonNode readBlocks(String blocksJson) throws JsonProcessingException {
        return objectMapper.readTree(blocksJson);
    }

    private String resolveToken(String teamId) {
        Workspace workspace = workspaceRepository.findByTeamId(teamId)
                                                 .orElseThrow(() -> OutboxWorkspaceNotFoundException.forTeamId(teamId));

        return workspace.getAccessToken();
    }

    private SlackNotificationOutboxHistory markFailureStatus(SlackNotificationOutbox outbox, Exception exception) {
        String reason = resolveFailureReason(exception);

        if (retryExceptionClassifier.isNotRetryable(exception)) {
            return outbox.markFailed(clock.instant(), reason, SlackInteractionFailureType.BUSINESS_INVARIANT);
        }

        if (outbox.getProcessingAttempt() < interactionRetryProperties.outbox().maxAttempts()) {
            return outbox.markRetryPending(clock.instant(), reason);
        }

        return outbox.markFailed(clock.instant(), reason, SlackInteractionFailureType.RETRY_EXHAUSTED);
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
            logLeaseRenewLost(outbox.getId(), currentProcessingStartedAt.get(), renewedProcessingStartedAt);
            throw new OutboxProcessingLeaseLostException("outbox processing lease를 상실했습니다.");
        }

        currentProcessingStartedAt.set(renewedProcessingStartedAt);
        outbox.renewProcessingLease(renewedProcessingStartedAt);
    }

    private boolean hasProcessingLease(
            SlackNotificationOutbox outbox,
            Instant claimedProcessingStartedAt
    ) {
        BoxProcessingLease processingLease = outbox.getProcessingLease();
        if (!processingLease.isClaimed()) {
            return false;
        }

        return claimedProcessingStartedAt.equals(processingLease.startedAt());
    }

    private void logLeaseLost(
            Long outboxId,
            Instant claimedProcessingStartedAt,
            BoxProcessingLease actualProcessingLease
    ) {
        if (actualProcessingLease.isClaimed()) {
            log.warn(
                    "outbox 처리 lease를 상실해 최종 상태 저장을 건너뜁니다. outboxId={}, claimedProcessingStartedAt={}, actualProcessingLeaseClaimed=true, actualProcessingStartedAt={}",
                    outboxId,
                    claimedProcessingStartedAt,
                    actualProcessingLease.startedAt()
            );
            return;
        }

        log.warn(
                "outbox 처리 lease를 상실해 최종 상태 저장을 건너뜁니다. outboxId={}, claimedProcessingStartedAt={}, actualProcessingLeaseClaimed=false",
                outboxId,
                claimedProcessingStartedAt
        );
    }

    private void logPersistLeaseLost(
            Long outboxId,
            Instant claimedProcessingStartedAt
    ) {
        slackNotificationOutboxRepository.findById(outboxId)
                                         .ifPresentOrElse(
                                                 actualOutbox -> logLeaseLost(
                                                         outboxId,
                                                         claimedProcessingStartedAt,
                                                         actualOutbox.getProcessingLease()
                                                 ),
                                                 () -> logLeaseLostWithUnknownActualState(
                                                         outboxId,
                                                         claimedProcessingStartedAt
                                                 )
                                         );
    }

    private void logLeaseLostWithUnknownActualState(
            Long outboxId,
            Instant claimedProcessingStartedAt
    ) {
        log.warn(
                "outbox 처리 lease를 상실해 최종 상태 저장을 건너뛰었지만 현재 row를 다시 조회하지 못했습니다. outboxId={}, claimedProcessingStartedAt={}",
                outboxId,
                claimedProcessingStartedAt
        );
    }

    private void logLeaseRenewLost(
            Long outboxId,
            Instant claimedProcessingStartedAt,
            Instant renewedProcessingStartedAt
    ) {
        log.warn(
                "outbox 처리 lease 갱신 중 lease를 상실했습니다. outboxId={}, claimedProcessingStartedAt={}, renewedProcessingStartedAt={}",
                outboxId,
                claimedProcessingStartedAt,
                renewedProcessingStartedAt
        );
    }
}

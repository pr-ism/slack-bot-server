package com.slack.bot.application.review.box.out;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.slack.bot.application.interaction.box.BoxFailureReasonTruncator;
import com.slack.bot.application.interaction.box.out.exception.OutboxProcessingLeaseLostException;
import com.slack.bot.application.interaction.box.retry.InteractionRetryExceptionClassifier;
import com.slack.bot.application.review.dto.ReviewMessageDto;
import com.slack.bot.application.worker.PollingHintPublisher;
import com.slack.bot.application.worker.PollingHintTarget;
import com.slack.bot.domain.workspace.Workspace;
import com.slack.bot.domain.workspace.repository.WorkspaceRepository;
import com.slack.bot.global.config.properties.InteractionRetryProperties;
import com.slack.bot.global.config.properties.ReviewWorkerProperties;
import com.slack.bot.infrastructure.interaction.box.SlackInteractionFailureType;
import com.slack.bot.infrastructure.interaction.client.NotificationTransportApiClient;
import com.slack.bot.infrastructure.review.box.out.ReviewNotificationOutbox;
import com.slack.bot.infrastructure.review.box.out.ReviewNotificationOutboxHistory;
import com.slack.bot.infrastructure.review.box.out.repository.ReviewNotificationOutboxRepository;
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
public class ReviewNotificationOutboxProcessor {

    private static final String PROCESSING_TIMEOUT_FAILURE_REASON =
            "PROCESSING 타임아웃으로 복구 처리되었습니다.";
    private static final String UNKNOWN_FAILURE_REASON = "unknown failure";

    private final Clock clock;
    private final ObjectMapper objectMapper;
    private final ReviewNotificationMessageRenderer reviewNotificationMessageRenderer;
    private final RetryTemplate slackNotificationOutboxRetryTemplate;
    private final WorkspaceRepository workspaceRepository;
    private final BoxFailureReasonTruncator failureReasonTruncator;
    private final InteractionRetryProperties interactionRetryProperties;
    private final ReviewWorkerProperties reviewWorkerProperties;
    private final PollingHintPublisher pollingHintPublisher;
    private final NotificationTransportApiClient notificationTransportApiClient;
    private final ReviewNotificationOutboxRepository reviewNotificationOutboxRepository;
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
        return reviewNotificationOutboxRepository.claimNextId(
                    claimedProcessingStartedAt,
                    claimedOutboxIds
            )
            .map(claimedOutboxId -> {
                claimedOutboxIds.add(claimedOutboxId);
                processSafely(claimedOutboxId, claimedProcessingStartedAt);
                return claimedCount + 1;
            })
            .orElse(claimedCount);
    }

    public int recoverTimeoutProcessing(long processingTimeoutMs) {
        validateProcessingTimeoutMs(processingTimeoutMs);

        Instant now = clock.instant();
        int recoveredCount = reviewNotificationOutboxRepository.recoverTimeoutProcessing(
                now.minusMillis(processingTimeoutMs),
                now,
                PROCESSING_TIMEOUT_FAILURE_REASON,
                interactionRetryProperties.outbox().maxAttempts(),
                reviewWorkerProperties.outbox().timeoutRecoveryBatchSize()
        );

        if (recoveredCount > 0) {
            pollingHintPublisher.publish(PollingHintTarget.REVIEW_NOTIFICATION_OUTBOX);
            log.warn("review_notification outbox PROCESSING 고착 건을 복구했습니다. count={}", recoveredCount);
        }

        return recoveredCount;
    }

    private void processSafely(Long outboxId, Instant claimedProcessingStartedAt) {
        reviewNotificationOutboxRepository.findById(outboxId)
                                          .ifPresentOrElse(
                                                  outbox -> processClaimedOutboxSafely(
                                                          outbox,
                                                          outboxId,
                                                          claimedProcessingStartedAt
                                                  ),
                                                  () -> log.warn(
                                                          "PROCESSING으로 전이된 review_notification outbox를 조회하지 못했습니다. outboxId={}",
                                                          outboxId
                                                  )
                                          );
    }

    private void processClaimedOutboxSafely(
            ReviewNotificationOutbox outbox,
            Long outboxId,
            Instant claimedProcessingStartedAt
    ) {
        try {
            processClaimedOutbox(outbox, claimedProcessingStartedAt);
        } catch (Exception unexpected) {
            log.error(
                    "review_notification outbox 처리 중 예기치 못한 예외가 발생했습니다. outboxId={}",
                    outboxId,
                    unexpected
            );
        }
    }

    private void processClaimedOutbox(
            ReviewNotificationOutbox outbox,
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
                return true;
            });
        } catch (OutboxProcessingLeaseLostException e) {
            return;
        } catch (Exception exception) {
            log.warn("review_notification outbox 처리에 실패했습니다. outboxId={}", outbox.getId(), exception);

            ReviewNotificationOutboxHistory history = markFailureStatus(outbox, exception);
            try {
                boolean updated = reviewNotificationOutboxRepository.saveIfProcessingLeaseMatched(
                        outbox,
                        history,
                        currentProcessingStartedAt.get()
                );
                if (updated) {
                    return;
                }

                logLeaseLost(outbox.getId(), currentProcessingStartedAt.get(), outbox.getProcessingStartedAt());
            } catch (Exception e) {
                log.error(
                        "review_notification outbox 실패 상태 저장에 실패했습니다. outboxId={}",
                        outbox.getId(),
                        e
                );
            }
            return;
        }

        ReviewNotificationOutboxHistory history = outbox.markSent(clock.instant());
        try {
            boolean updated = reviewNotificationOutboxRepository.saveIfProcessingLeaseMatched(
                    outbox,
                    history,
                    currentProcessingStartedAt.get()
            );
            if (updated) {
                return;
            }

            logLeaseLost(outbox.getId(), currentProcessingStartedAt.get(), outbox.getProcessingStartedAt());
        } catch (Exception e) {
            log.error(
                    "review_notification outbox 전송은 성공했지만 SENT 상태 저장에 실패했습니다. outboxId={}",
                    outbox.getId(),
                    e
            );
        }
    }

    private void dispatch(ReviewNotificationOutbox outbox) throws JsonProcessingException {
        String token = resolveToken(outbox.getTeamId());
        ReviewMessageDto message = renderMessage(outbox);

        notificationTransportApiClient.sendBlockMessage(
                token,
                outbox.getChannelId(),
                message.blocks(),
                message.attachments(),
                message.fallbackText()
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

    private ReviewMessageDto renderMessage(ReviewNotificationOutbox outbox) throws JsonProcessingException {
        if (outbox.hasSemanticPayload()) {
            return reviewNotificationMessageRenderer.render(outbox);
        }

        return new ReviewMessageDto(
                readBlocks(outbox.getBlocksJson()),
                readAttachments(outbox.getAttachmentsJson()),
                outbox.getFallbackText()
        );
    }

    private JsonNode readAttachments(String attachmentsJson) throws JsonProcessingException {
        if (attachmentsJson == null || attachmentsJson.isBlank()) {
            return null;
        }

        return objectMapper.readTree(attachmentsJson);
    }

    private ReviewNotificationOutboxHistory markFailureStatus(
            ReviewNotificationOutbox outbox,
            Exception exception
    ) {
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

    private void validateProcessingTimeoutMs(long processingTimeoutMs) {
        if (processingTimeoutMs <= 0) {
            throw new IllegalArgumentException("processingTimeoutMs는 0보다 커야 합니다.");
        }
    }

    private Instant currentLeaseStartedAt() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }

    private void renewProcessingLease(
            ReviewNotificationOutbox outbox,
            AtomicReference<Instant> currentProcessingStartedAt
    ) {
        Instant renewedProcessingStartedAt = currentLeaseStartedAt();
        boolean renewed = reviewNotificationOutboxRepository.renewProcessingLease(
                outbox.getId(),
                currentProcessingStartedAt.get(),
                renewedProcessingStartedAt
        );
        if (!renewed) {
            logLeaseLost(outbox.getId(), currentProcessingStartedAt.get(), renewedProcessingStartedAt);
            throw new OutboxProcessingLeaseLostException("review_notification outbox processing lease를 상실했습니다.");
        }

        currentProcessingStartedAt.set(renewedProcessingStartedAt);
        outbox.renewProcessingLease(renewedProcessingStartedAt);
    }

    private boolean hasProcessingLease(
            ReviewNotificationOutbox outbox,
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
                "review_notification outbox 처리 lease를 상실해 최종 상태 저장을 건너뜁니다. outboxId={}, claimedProcessingStartedAt={}, actualProcessingStartedAt={}",
                outboxId,
                claimedProcessingStartedAt,
                actualProcessingStartedAt
        );
    }
}

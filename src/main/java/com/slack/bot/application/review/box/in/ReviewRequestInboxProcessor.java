package com.slack.bot.application.review.box.in;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.slack.bot.application.interactivity.box.BoxFailureReasonTruncator;
import com.slack.bot.application.interactivity.box.retry.InteractionRetryExceptionClassifier;
import com.slack.bot.application.review.ReviewNotificationService;
import com.slack.bot.application.review.box.ReviewNotificationSourceContext;
import com.slack.bot.application.review.dto.ReviewNotificationPayload;
import com.slack.bot.global.config.properties.InteractionRetryProperties;
import com.slack.bot.infrastructure.review.box.in.ReviewRequestInbox;
import com.slack.bot.infrastructure.review.box.in.ReviewRequestInboxFailureType;
import com.slack.bot.infrastructure.review.box.in.ReviewRequestInboxStatus;
import com.slack.bot.infrastructure.review.box.in.repository.ReviewRequestInboxRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ReviewRequestInboxProcessor {

    private static final String PROCESSING_TIMEOUT_FAILURE_REASON =
            "PROCESSING 타임아웃으로 복구 처리되었습니다.";
    private static final String UNKNOWN_FAILURE_REASON = "unknown failure";
    private static final String REVIEW_REQUEST_INBOX_SOURCE_PREFIX = "REVIEW_REQUEST_INBOX";

    private final Clock clock;
    private final ObjectMapper objectMapper;
    private final ReviewNotificationService reviewNotificationService;
    private final ReviewNotificationSourceContext reviewNotificationSourceContext;
    private final BoxFailureReasonTruncator failureReasonTruncator;
    private final InteractionRetryProperties interactionRetryProperties;
    private final ReviewRequestInboxRepository reviewRequestInboxRepository;
    private final InteractionRetryExceptionClassifier retryExceptionClassifier;

    public void enqueue(String apiKey, ReviewNotificationPayload request, long batchWindowMillis) {
        String coalescingKey = buildCoalescingKey(apiKey, request);
        enqueue(apiKey, request, batchWindowMillis, coalescingKey);
    }

    public void enqueue(
            String apiKey,
            ReviewNotificationPayload request,
            long batchWindowMillis,
            String coalescingKey
    ) {
        validateBatchWindowMillis(batchWindowMillis);
        if (request == null) {
            throw new IllegalArgumentException("request는 비어 있을 수 없습니다.");
        }

        validateCoalescingKey(coalescingKey);
        String requestJson = serializeRequest(request);
        Instant availableAt = clock.instant().plusMillis(batchWindowMillis);

        reviewRequestInboxRepository.upsertPending(
                coalescingKey,
                apiKey,
                request.githubPullRequestId(),
                requestJson,
                availableAt
        );
    }

    public void processPending(int limit, long processingTimeoutMs) {
        validateProcessingTimeoutMs(processingTimeoutMs);

        recoverTimeoutProcessing(processingTimeoutMs);

        Instant now = clock.instant();
        List<ReviewRequestInbox> pendings = reviewRequestInboxRepository.findClaimable(now, limit);

        for (ReviewRequestInbox pending : pendings) {
            processSafely(pending);
        }
    }

    private void recoverTimeoutProcessing(long processingTimeoutMs) {
        Instant now = clock.instant();
        int recoveredCount = reviewRequestInboxRepository.recoverTimeoutProcessing(
                now.minusMillis(processingTimeoutMs),
                now,
                PROCESSING_TIMEOUT_FAILURE_REASON,
                interactionRetryProperties.inbox().maxAttempts()
        );

        if (recoveredCount > 0) {
            log.warn("review_request inbox PROCESSING 고착 건을 복구했습니다. count={}", recoveredCount);
        }
    }

    private void processSafely(ReviewRequestInbox pending) {
        Long inboxId = pending.getId();
        if (inboxId == null) {
            return;
        }

        Instant claimNow = clock.instant();
        if (!reviewRequestInboxRepository.markProcessingIfClaimable(inboxId, claimNow, claimNow)) {
            return;
        }

        reviewRequestInboxRepository.findById(inboxId)
                                    .ifPresentOrElse(
                                            inbox -> processClaimedInbox(inbox),
                                            () -> log.warn(
                                                    "PROCESSING으로 전이된 review_request inbox를 조회하지 못했습니다. inboxId={}",
                                                    inboxId
                                            )
                                    );
    }

    private void processClaimedInbox(ReviewRequestInbox inbox) {
        try {
            ReviewNotificationPayload request = objectMapper.readValue(
                    inbox.getRequestJson(),
                    ReviewNotificationPayload.class
            );

            String sourceKey = resolveSourceKey(inbox);
            reviewNotificationSourceContext.withSourceKey(
                    sourceKey,
                    () -> reviewNotificationService.sendSimpleNotification(inbox.getApiKey(), request)
            );

            inbox.markProcessed(clock.instant());
            reviewRequestInboxRepository.save(inbox);
        } catch (Exception e) {
            log.error("review_request inbox 처리에 실패했습니다. inboxId={}", inbox.getId(), e);

            markFailureStatus(inbox, e);
            reviewRequestInboxRepository.save(inbox);
        }
    }

    private void markFailureStatus(ReviewRequestInbox inbox, Exception exception) {
        if (inbox.getStatus() != ReviewRequestInboxStatus.PROCESSING) {
            log.warn(
                    "PROCESSING이 아닌 상태에서 실패 처리를 시도했습니다. inboxId={}, status={}",
                    inbox.getId(),
                    inbox.getStatus()
            );
            return;
        }

        String reason = resolveFailureReason(exception);

        if (!retryExceptionClassifier.isRetryable(exception)) {
            inbox.markFailed(clock.instant(), reason, ReviewRequestInboxFailureType.NON_RETRYABLE);
            return;
        }

        if (inbox.getProcessingAttempt() < interactionRetryProperties.inbox().maxAttempts()) {
            inbox.markRetryPending(clock.instant(), reason);
            return;
        }

        inbox.markFailed(clock.instant(), reason, ReviewRequestInboxFailureType.RETRY_EXHAUSTED);
    }

    private String buildCoalescingKey(String apiKey, ReviewNotificationPayload request) {
        if (request == null) {
            throw new IllegalArgumentException("request는 비어 있을 수 없습니다.");
        }

        Long githubPullRequestId = request.githubPullRequestId();

        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException("apiKey는 비어 있을 수 없습니다.");
        }
        if (githubPullRequestId == null || githubPullRequestId <= 0) {
            throw new IllegalArgumentException("githubPullRequestId는 비어 있을 수 없습니다.");
        }

        return apiKey + ":" + githubPullRequestId;
    }

    private void validateCoalescingKey(String coalescingKey) {
        if (coalescingKey == null || coalescingKey.isBlank()) {
            throw new IllegalArgumentException("coalescingKey는 비어 있을 수 없습니다.");
        }
    }

    private String serializeRequest(ReviewNotificationPayload request) {
        try {
            return objectMapper.writeValueAsString(request);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("review request 직렬화에 실패했습니다.", e);
        }
    }

    private String resolveFailureReason(Exception exception) {
        String reason = failureReasonTruncator.truncate(exception.getMessage());

        if (reason == null || reason.isBlank()) {
            return UNKNOWN_FAILURE_REASON;
        }

        return reason;
    }

    private String resolveSourceKey(ReviewRequestInbox inbox) {
        long availableAtEpochMillis = resolveAvailableAtEpochMillis(inbox.getAvailableAt());

        return REVIEW_REQUEST_INBOX_SOURCE_PREFIX
                + ":" + inbox.getCoalescingKey()
                + ":" + availableAtEpochMillis;
    }

    private long resolveAvailableAtEpochMillis(Instant availableAt) {
        if (availableAt == null) {
            return 0L;
        }

        return availableAt.toEpochMilli();
    }

    private void validateBatchWindowMillis(long batchWindowMillis) {
        if (batchWindowMillis < 0) {
            throw new IllegalArgumentException("batchWindowMillis는 0 이상이어야 합니다.");
        }
    }

    private void validateProcessingTimeoutMs(long processingTimeoutMs) {
        if (processingTimeoutMs <= 0) {
            throw new IllegalArgumentException("processingTimeoutMs는 0보다 커야 합니다.");
        }
    }
}

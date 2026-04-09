package com.slack.bot.application.review.box.in;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.slack.bot.application.review.box.ReviewNotificationIdempotencyKeyGenerator;
import com.slack.bot.application.review.box.ReviewNotificationIdempotencyScope;
import com.slack.bot.application.review.dto.ReviewNotificationPayload;
import com.slack.bot.application.worker.PollingHintPublisher;
import com.slack.bot.application.worker.PollingHintTarget;
import com.slack.bot.global.config.properties.InteractionRetryProperties;
import com.slack.bot.global.config.properties.ReviewWorkerProperties;
import com.slack.bot.infrastructure.review.box.in.repository.ReviewRequestInboxRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ReviewRequestInboxProcessor {

    private static final String PROCESSING_TIMEOUT_FAILURE_REASON =
            "PROCESSING 타임아웃으로 복구 처리되었습니다.";

    private final Clock clock;
    private final ObjectMapper objectMapper;
    private final InteractionRetryProperties interactionRetryProperties;
    private final ReviewWorkerProperties reviewWorkerProperties;
    private final ReviewRequestInboxRepository reviewRequestInboxRepository;
    private final ReviewRequestInboxEntryProcessor reviewRequestInboxEntryProcessor;
    private final ReviewNotificationIdempotencyKeyGenerator idempotencyKeyGenerator;
    private final ReviewRequestInboxIdempotencyPayloadEncoder idempotencyPayloadEncoder;
    private final PollingHintPublisher pollingHintPublisher;

    public void enqueue(String apiKey, ReviewNotificationPayload request, long batchWindowMillis) {
        validateBatchWindowMillis(batchWindowMillis);
        if (request == null) {
            throw new IllegalArgumentException("request는 비어 있을 수 없습니다.");
        }

        validateApiKeyAndGithubPullRequestId(apiKey, request.githubPullRequestId());
        String requestJson = serializeRequest(request);
        String idempotencyKey = buildIdempotencyKey(apiKey, request);

        enqueuePending(apiKey, request.githubPullRequestId(), requestJson, batchWindowMillis, idempotencyKey);
    }

    public int processPending(int limit) {
        Set<Long> claimedInboxIds = new HashSet<>();
        int claimedCount = 0;
        for (int count = 0; count < limit; count++) {
            Instant claimNow = currentLeaseStartedAt();
            if (!claimAndProcessNext(claimedInboxIds, claimNow)) {
                return claimedCount;
            }

            claimedCount++;
        }

        return claimedCount;
    }

    public int recoverTimeoutProcessing(long processingTimeoutMs) {
        validateProcessingTimeoutMs(processingTimeoutMs);

        Instant now = clock.instant();
        int recoveredCount = reviewRequestInboxRepository.recoverTimeoutProcessing(
                now.minusMillis(processingTimeoutMs),
                now,
                PROCESSING_TIMEOUT_FAILURE_REASON,
                interactionRetryProperties.inbox().maxAttempts(),
                reviewWorkerProperties.inbox().timeoutRecoveryBatchSize()
        );

        if (recoveredCount > 0) {
            pollingHintPublisher.publish(PollingHintTarget.REVIEW_REQUEST_INBOX);
            log.warn("review_request inbox PROCESSING 고착 건을 복구했습니다. count={}", recoveredCount);
        }

        return recoveredCount;
    }

    private void processSafely(Long inboxId, Instant claimedProcessingStartedAt) {
        try {
            reviewRequestInboxEntryProcessor.processClaimedInbox(inboxId, claimedProcessingStartedAt);
        } catch (Exception e) {
            log.error("review_request inbox 엔트리 처리 중 예상치 못한 오류가 발생했습니다. inboxId={}", inboxId, e);
        }
    }

    private boolean claimAndProcessNext(Set<Long> claimedInboxIds, Instant claimNow) {
        return reviewRequestInboxRepository.claimNextId(
                claimNow,
                claimNow,
                claimedInboxIds
        ).map(claimedInboxId -> {
            claimedInboxIds.add(claimedInboxId);
            processSafely(claimedInboxId, claimNow);
            return true;
        }).orElse(false);
    }

    private Instant currentLeaseStartedAt() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }

    private void validateApiKeyAndGithubPullRequestId(String apiKey, Long githubPullRequestId) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException("apiKey는 비어 있을 수 없습니다.");
        }
        if (githubPullRequestId == null || githubPullRequestId <= 0) {
            throw new IllegalArgumentException("githubPullRequestId는 비어 있을 수 없습니다.");
        }
    }

    private String buildIdempotencyKey(String apiKey, ReviewNotificationPayload request) {
        String idempotencyPayload = idempotencyPayloadEncoder.encode(apiKey, request);

        return idempotencyKeyGenerator.generate(
                ReviewNotificationIdempotencyScope.REVIEW_REQUEST_INBOX,
                idempotencyPayload
        );
    }

    private void enqueuePending(
            String apiKey,
            Long githubPullRequestId,
            String requestJson,
            long batchWindowMillis,
            String idempotencyKey
    ) {
        Instant availableAt = clock.instant().plusMillis(batchWindowMillis);

        reviewRequestInboxRepository.upsertPending(
                idempotencyKey,
                apiKey,
                githubPullRequestId,
                requestJson,
                availableAt
        );
        if (batchWindowMillis == 0L) {
            pollingHintPublisher.publish(PollingHintTarget.REVIEW_REQUEST_INBOX);
        }
    }

    private String serializeRequest(ReviewNotificationPayload request) {
        try {
            return objectMapper.writeValueAsString(request);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("review request 직렬화에 실패했습니다.", e);
        }
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

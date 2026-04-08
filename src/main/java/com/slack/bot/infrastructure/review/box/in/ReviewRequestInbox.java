package com.slack.bot.infrastructure.review.box.in;

import com.slack.bot.infrastructure.common.FailureSnapshotDefaults;
import java.time.Instant;
import lombok.Getter;

@Getter
public class ReviewRequestInbox {

    private final Long id;
    private final String idempotencyKey;
    private final String apiKey;
    private final Long githubPullRequestId;
    private final String requestJson;
    private final Instant availableAt;

    private ReviewRequestInboxStatus status;
    private int processingAttempt;
    private Instant processingStartedAt;
    private Instant processedAt;
    private Instant failedAt;
    private String failureReason;
    private ReviewRequestInboxFailureType failureType;

    public static ReviewRequestInbox pending(
            String idempotencyKey,
            String apiKey,
            Long githubPullRequestId,
            String requestJson,
            Instant availableAt
    ) {
        validateIdempotencyKey(idempotencyKey);
        validateApiKey(apiKey);
        validateGithubPullRequestId(githubPullRequestId);
        validateRequestJson(requestJson);
        validateAvailableAt(availableAt);

        return new ReviewRequestInbox(
                null,
                idempotencyKey,
                apiKey,
                githubPullRequestId,
                requestJson,
                availableAt,
                ReviewRequestInboxStatus.PENDING,
                0,
                FailureSnapshotDefaults.NO_PROCESSING_STARTED_AT,
                FailureSnapshotDefaults.NO_PROCESSED_AT,
                FailureSnapshotDefaults.NO_FAILURE_AT,
                FailureSnapshotDefaults.NO_FAILURE_REASON,
                ReviewRequestInboxFailureType.NONE
        );
    }

    public static ReviewRequestInbox rehydrate(
            Long id,
            String idempotencyKey,
            String apiKey,
            Long githubPullRequestId,
            String requestJson,
            Instant availableAt,
            ReviewRequestInboxStatus status,
            int processingAttempt,
            Instant processingStartedAt,
            Instant processedAt,
            Instant failedAt,
            String failureReason,
            ReviewRequestInboxFailureType failureType
    ) {
        validateIdempotencyKey(idempotencyKey);
        validateApiKey(apiKey);
        validateGithubPullRequestId(githubPullRequestId);
        validateRequestJson(requestJson);
        validateAvailableAt(availableAt);
        validateStatus(status);
        validateProcessingAttempt(processingAttempt);
        validateProcessingStartedAt(processingStartedAt);
        validateProcessedAt(processedAt);
        validateFailedAt(failedAt);
        validateFailureReasonSnapshot(failureReason);
        validateFailureTypeSnapshot(failureType);

        return new ReviewRequestInbox(
                id,
                idempotencyKey,
                apiKey,
                githubPullRequestId,
                requestJson,
                availableAt,
                status,
                processingAttempt,
                processingStartedAt,
                processedAt,
                failedAt,
                failureReason,
                failureType
        );
    }

    private ReviewRequestInbox(
            Long id,
            String idempotencyKey,
            String apiKey,
            Long githubPullRequestId,
            String requestJson,
            Instant availableAt,
            ReviewRequestInboxStatus status,
            int processingAttempt,
            Instant processingStartedAt,
            Instant processedAt,
            Instant failedAt,
            String failureReason,
            ReviewRequestInboxFailureType failureType
    ) {
        this.id = id;
        this.idempotencyKey = idempotencyKey;
        this.apiKey = apiKey;
        this.githubPullRequestId = githubPullRequestId;
        this.requestJson = requestJson;
        this.availableAt = availableAt;
        this.status = status;
        this.processingAttempt = processingAttempt;
        this.processingStartedAt = processingStartedAt;
        this.processedAt = processedAt;
        this.failedAt = failedAt;
        this.failureReason = failureReason;
        this.failureType = failureType;
    }

    public ReviewRequestInboxHistory markProcessed(Instant processedAt) {
        validatePresentProcessedAt(processedAt);
        validateTransition(ReviewRequestInboxStatus.PROCESSING, "PROCESSED");

        this.status = ReviewRequestInboxStatus.PROCESSED;
        this.processingStartedAt = FailureSnapshotDefaults.NO_PROCESSING_STARTED_AT;
        this.processedAt = processedAt;
        this.failedAt = FailureSnapshotDefaults.NO_FAILURE_AT;
        this.failureReason = FailureSnapshotDefaults.NO_FAILURE_REASON;
        this.failureType = ReviewRequestInboxFailureType.NONE;

        return ReviewRequestInboxHistory.completed(
                getId(),
                this.processingAttempt,
                ReviewRequestInboxStatus.PROCESSED,
                processedAt,
                FailureSnapshotDefaults.NO_FAILURE_REASON,
                ReviewRequestInboxFailureType.NONE
        );
    }

    public void renewProcessingLease(Instant processingStartedAt) {
        validatePresentProcessingStartedAt(processingStartedAt);
        validateTransition(ReviewRequestInboxStatus.PROCESSING, "PROCESSING");

        this.processingStartedAt = processingStartedAt;
    }

    public ReviewRequestInboxHistory markRetryPending(Instant failedAt, String failureReason) {
        validatePresentFailedAt(failedAt);
        validatePresentFailureReason(failureReason);
        validateTransition(ReviewRequestInboxStatus.PROCESSING, "RETRY_PENDING");

        this.status = ReviewRequestInboxStatus.RETRY_PENDING;
        this.processingStartedAt = FailureSnapshotDefaults.NO_PROCESSING_STARTED_AT;
        this.processedAt = FailureSnapshotDefaults.NO_PROCESSED_AT;
        this.failedAt = failedAt;
        this.failureReason = failureReason;
        this.failureType = ReviewRequestInboxFailureType.NONE;

        return ReviewRequestInboxHistory.completed(
                getId(),
                this.processingAttempt,
                ReviewRequestInboxStatus.RETRY_PENDING,
                failedAt,
                failureReason,
                ReviewRequestInboxFailureType.NONE
        );
    }

    public ReviewRequestInboxHistory markFailed(
            Instant failedAt,
            String failureReason,
            ReviewRequestInboxFailureType failureType
    ) {
        validatePresentFailedAt(failedAt);
        validatePresentFailureReason(failureReason);
        validatePresentFailureType(failureType);
        validateTransition(ReviewRequestInboxStatus.PROCESSING, "FAILED");

        this.status = ReviewRequestInboxStatus.FAILED;
        this.processingStartedAt = FailureSnapshotDefaults.NO_PROCESSING_STARTED_AT;
        this.processedAt = FailureSnapshotDefaults.NO_PROCESSED_AT;
        this.failedAt = failedAt;
        this.failureReason = failureReason;
        this.failureType = failureType;

        return ReviewRequestInboxHistory.completed(
                getId(),
                this.processingAttempt,
                ReviewRequestInboxStatus.FAILED,
                failedAt,
                failureReason,
                failureType
        );
    }

    private static void validateIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("idempotencyKey는 비어 있을 수 없습니다.");
        }
    }

    private static void validateApiKey(String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException("apiKey는 비어 있을 수 없습니다.");
        }
    }

    private static void validateGithubPullRequestId(Long githubPullRequestId) {
        if (githubPullRequestId == null || githubPullRequestId <= 0) {
            throw new IllegalArgumentException("githubPullRequestId는 비어 있을 수 없습니다.");
        }
    }

    private static void validateRequestJson(String requestJson) {
        if (requestJson == null || requestJson.isBlank()) {
            throw new IllegalArgumentException("requestJson은 비어 있을 수 없습니다.");
        }
    }

    private static void validateAvailableAt(Instant availableAt) {
        if (availableAt == null) {
            throw new IllegalArgumentException("availableAt은 비어 있을 수 없습니다.");
        }
    }

    private static void validateStatus(ReviewRequestInboxStatus status) {
        if (status == null) {
            throw new IllegalArgumentException("status는 비어 있을 수 없습니다.");
        }
    }

    private static void validateProcessingAttempt(int processingAttempt) {
        if (processingAttempt < 0) {
            throw new IllegalArgumentException("processingAttempt는 0 이상이어야 합니다.");
        }
    }

    private static void validateProcessingStartedAt(Instant processingStartedAt) {
        if (processingStartedAt == null) {
            throw new IllegalArgumentException("processingStartedAt은 비어 있을 수 없습니다.");
        }
    }

    private static void validateProcessedAt(Instant processedAt) {
        if (processedAt == null) {
            throw new IllegalArgumentException("processedAt은 비어 있을 수 없습니다.");
        }
    }

    private static void validateFailedAt(Instant failedAt) {
        if (failedAt == null) {
            throw new IllegalArgumentException("failedAt은 비어 있을 수 없습니다.");
        }
    }

    private static void validateFailureReasonSnapshot(String failureReason) {
        if (failureReason == null) {
            throw new IllegalArgumentException("failureReason은 비어 있을 수 없습니다.");
        }
    }

    private static void validateFailureTypeSnapshot(ReviewRequestInboxFailureType failureType) {
        if (failureType == null) {
            throw new IllegalArgumentException("failureType은 비어 있을 수 없습니다.");
        }
    }

    private void validatePresentProcessedAt(Instant processedAt) {
        if (processedAt == null) {
            throw new IllegalArgumentException("processedAt은 비어 있을 수 없습니다.");
        }
    }

    private void validatePresentProcessingStartedAt(Instant processingStartedAt) {
        if (processingStartedAt == null) {
            throw new IllegalArgumentException("processingStartedAt은 비어 있을 수 없습니다.");
        }
    }

    private void validatePresentFailedAt(Instant failedAt) {
        if (failedAt == null) {
            throw new IllegalArgumentException("failedAt은 비어 있을 수 없습니다.");
        }
    }

    private void validatePresentFailureReason(String failureReason) {
        if (failureReason == null || failureReason.isBlank()) {
            throw new IllegalArgumentException("failureReason은 비어 있을 수 없습니다.");
        }
    }

    private void validatePresentFailureType(ReviewRequestInboxFailureType failureType) {
        if (failureType == null || failureType == ReviewRequestInboxFailureType.NONE) {
            throw new IllegalArgumentException("failureType은 NONE일 수 없습니다.");
        }
    }

    private void validateTransition(ReviewRequestInboxStatus expectedStatus, String targetStatus) {
        if (status == expectedStatus) {
            return;
        }

        throw new IllegalStateException(
                targetStatus + " 전이는 " + expectedStatus + " 상태에서만 가능합니다. 현재: " + status
        );
    }
}

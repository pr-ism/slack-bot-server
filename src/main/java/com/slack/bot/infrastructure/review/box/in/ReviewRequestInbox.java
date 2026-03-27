package com.slack.bot.infrastructure.review.box.in;

import com.slack.bot.domain.common.BaseTimeEntity;
import com.slack.bot.infrastructure.common.FailureSnapshotDefaults;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "review_request_inbox")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReviewRequestInbox extends BaseTimeEntity {

    private String idempotencyKey;

    private String apiKey;

    private Long githubPullRequestId;

    private String requestJson;

    private Instant availableAt;

    @Enumerated(EnumType.STRING)
    private ReviewRequestInboxStatus status;

    private int processingAttempt;

    private Instant processingStartedAt;

    private Instant processedAt;

    private Instant failedAt;

    private String failureReason;

    @Enumerated(EnumType.STRING)
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
                idempotencyKey,
                apiKey,
                githubPullRequestId,
                requestJson,
                availableAt,
                ReviewRequestInboxStatus.PENDING,
                0
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

    private ReviewRequestInbox(
            String idempotencyKey,
            String apiKey,
            Long githubPullRequestId,
            String requestJson,
            Instant availableAt,
            ReviewRequestInboxStatus status,
            int processingAttempt
    ) {
        this.idempotencyKey = idempotencyKey;
        this.apiKey = apiKey;
        this.githubPullRequestId = githubPullRequestId;
        this.requestJson = requestJson;
        this.availableAt = availableAt;
        this.status = status;
        this.processingAttempt = processingAttempt;
        this.failedAt = FailureSnapshotDefaults.NO_FAILURE_AT;
        this.failureReason = FailureSnapshotDefaults.NO_FAILURE_REASON;
        this.failureType = ReviewRequestInboxFailureType.NONE;
    }

    public ReviewRequestInboxHistory markProcessed(Instant processedAt) {
        validateProcessedAt(processedAt);
        validateTransition(ReviewRequestInboxStatus.PROCESSING, "PROCESSED");

        this.status = ReviewRequestInboxStatus.PROCESSED;
        this.processingStartedAt = null;
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
        validateProcessingStartedAt(processingStartedAt);
        validateTransition(ReviewRequestInboxStatus.PROCESSING, "PROCESSING");

        this.processingStartedAt = processingStartedAt;
    }

    public ReviewRequestInboxHistory markRetryPending(Instant failedAt, String failureReason) {
        validateFailedAt(failedAt);
        validateFailureReason(failureReason);
        validateTransition(ReviewRequestInboxStatus.PROCESSING, "RETRY_PENDING");

        this.status = ReviewRequestInboxStatus.RETRY_PENDING;
        this.processingStartedAt = null;
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
        validateFailedAt(failedAt);
        validateFailureReason(failureReason);
        validateFailureType(failureType);
        validateTransition(ReviewRequestInboxStatus.PROCESSING, "FAILED");

        this.status = ReviewRequestInboxStatus.FAILED;
        this.processingStartedAt = null;
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

    private void validateProcessedAt(Instant processedAt) {
        if (processedAt == null) {
            throw new IllegalArgumentException("processedAt은 비어 있을 수 없습니다.");
        }
    }

    private void validateProcessingStartedAt(Instant processingStartedAt) {
        if (processingStartedAt == null) {
            throw new IllegalArgumentException("processingStartedAt은 비어 있을 수 없습니다.");
        }
    }

    private void validateFailedAt(Instant failedAt) {
        if (failedAt == null) {
            throw new IllegalArgumentException("failedAt은 비어 있을 수 없습니다.");
        }
    }

    private void validateFailureReason(String failureReason) {
        if (failureReason == null || failureReason.isBlank()) {
            throw new IllegalArgumentException("failureReason은 비어 있을 수 없습니다.");
        }
    }

    private void validateFailureType(ReviewRequestInboxFailureType failureType) {
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

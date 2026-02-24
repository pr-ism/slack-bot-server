package com.slack.bot.infrastructure.review.box.in;

import com.slack.bot.domain.common.BaseTimeEntity;
import com.slack.bot.infrastructure.interaction.box.SlackInteractivityFailureType;
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

    private String coalescingKey;

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
    private SlackInteractivityFailureType failureType;

    public static ReviewRequestInbox pending(
            String coalescingKey,
            String apiKey,
            Long githubPullRequestId,
            String requestJson,
            Instant availableAt
    ) {
        validateCoalescingKey(coalescingKey);
        validateApiKey(apiKey);
        validateGithubPullRequestId(githubPullRequestId);
        validateRequestJson(requestJson);
        validateAvailableAt(availableAt);

        return new ReviewRequestInbox(
                coalescingKey,
                apiKey,
                githubPullRequestId,
                requestJson,
                availableAt,
                ReviewRequestInboxStatus.PENDING,
                0
        );
    }

    private static void validateCoalescingKey(String coalescingKey) {
        if (coalescingKey == null || coalescingKey.isBlank()) {
            throw new IllegalArgumentException("coalescingKey는 비어 있을 수 없습니다.");
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
            String coalescingKey,
            String apiKey,
            Long githubPullRequestId,
            String requestJson,
            Instant availableAt,
            ReviewRequestInboxStatus status,
            int processingAttempt
    ) {
        this.coalescingKey = coalescingKey;
        this.apiKey = apiKey;
        this.githubPullRequestId = githubPullRequestId;
        this.requestJson = requestJson;
        this.availableAt = availableAt;
        this.status = status;
        this.processingAttempt = processingAttempt;
    }

    public void markProcessing(Instant processingStartedAt) {
        validateProcessingStartedAt(processingStartedAt);
        validateProcessingTransition();

        this.status = ReviewRequestInboxStatus.PROCESSING;
        this.processingStartedAt = processingStartedAt;
        this.processingAttempt += 1;
        this.failedAt = null;
        this.failureReason = null;
        this.failureType = null;
    }

    public void markProcessed(Instant processedAt) {
        validateProcessedAt(processedAt);
        validateTransition(ReviewRequestInboxStatus.PROCESSING, "PROCESSED");

        this.status = ReviewRequestInboxStatus.PROCESSED;
        this.processingStartedAt = null;
        this.processedAt = processedAt;
        this.failedAt = null;
        this.failureReason = null;
        this.failureType = null;
    }

    public void markRetryPending(Instant failedAt, String failureReason) {
        validateFailedAt(failedAt);
        validateFailureReason(failureReason);
        validateTransition(ReviewRequestInboxStatus.PROCESSING, "RETRY_PENDING");

        this.status = ReviewRequestInboxStatus.RETRY_PENDING;
        this.processingStartedAt = null;
        this.failedAt = failedAt;
        this.failureReason = failureReason;
        this.failureType = null;
    }

    public void markFailed(Instant failedAt, String failureReason, SlackInteractivityFailureType failureType) {
        validateFailedAt(failedAt);
        validateFailureReason(failureReason);
        validateFailureType(failureType);
        validateTransition(ReviewRequestInboxStatus.PROCESSING, "FAILED");

        this.status = ReviewRequestInboxStatus.FAILED;
        this.processingStartedAt = null;
        this.failedAt = failedAt;
        this.failureReason = failureReason;
        this.failureType = failureType;
    }

    private void validateProcessingStartedAt(Instant processingStartedAt) {
        if (processingStartedAt == null) {
            throw new IllegalArgumentException("processingStartedAt은 비어 있을 수 없습니다.");
        }
    }

    private void validateProcessedAt(Instant processedAt) {
        if (processedAt == null) {
            throw new IllegalArgumentException("processedAt은 비어 있을 수 없습니다.");
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

    private void validateFailureType(SlackInteractivityFailureType failureType) {
        if (failureType == null) {
            throw new IllegalArgumentException("failureType은 비어 있을 수 없습니다.");
        }
    }

    private void validateProcessingTransition() {
        if (status == ReviewRequestInboxStatus.PENDING || status == ReviewRequestInboxStatus.RETRY_PENDING) {
            return;
        }

        throw new IllegalStateException(
                "PROCESSING 전이는 PENDING 또는 RETRY_PENDING 상태에서만 가능합니다. 현재: " + status
        );
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


package com.slack.bot.infrastructure.review.box.in;

import com.slack.bot.infrastructure.common.BoxEventTime;
import com.slack.bot.infrastructure.common.BoxFailureSnapshot;
import com.slack.bot.infrastructure.common.BoxProcessingLease;
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
    private BoxProcessingLease processingLease;
    private BoxEventTime processedTime;
    private BoxEventTime failedTime;
    private BoxFailureSnapshot<ReviewRequestInboxFailureType> failure;

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
                BoxProcessingLease.idle(),
                BoxEventTime.absent(),
                BoxEventTime.absent(),
                BoxFailureSnapshot.absent()
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
            BoxProcessingLease processingLease,
            BoxEventTime processedTime,
            BoxEventTime failedTime,
            BoxFailureSnapshot<ReviewRequestInboxFailureType> failure
    ) {
        validateIdempotencyKey(idempotencyKey);
        validateApiKey(apiKey);
        validateGithubPullRequestId(githubPullRequestId);
        validateRequestJson(requestJson);
        validateAvailableAt(availableAt);
        validateStatus(status);
        validateProcessingAttempt(processingAttempt);
        validateProcessingLease(processingLease);
        validateProcessedTime(processedTime);
        validateFailedTime(failedTime);
        validateFailure(failure);
        validateState(status, processingAttempt, processingLease, processedTime, failedTime, failure);

        return new ReviewRequestInbox(
                id,
                idempotencyKey,
                apiKey,
                githubPullRequestId,
                requestJson,
                availableAt,
                status,
                processingAttempt,
                processingLease,
                processedTime,
                failedTime,
                failure
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
            BoxProcessingLease processingLease,
            BoxEventTime processedTime,
            BoxEventTime failedTime,
            BoxFailureSnapshot<ReviewRequestInboxFailureType> failure
    ) {
        this.id = id;
        this.idempotencyKey = idempotencyKey;
        this.apiKey = apiKey;
        this.githubPullRequestId = githubPullRequestId;
        this.requestJson = requestJson;
        this.availableAt = availableAt;
        this.status = status;
        this.processingAttempt = processingAttempt;
        this.processingLease = processingLease;
        this.processedTime = processedTime;
        this.failedTime = failedTime;
        this.failure = failure;
    }

    public ReviewRequestInboxHistory markProcessed(Instant processedAt) {
        validateProcessedAt(processedAt);
        validateTransition(ReviewRequestInboxStatus.PROCESSING, "PROCESSED");

        this.status = ReviewRequestInboxStatus.PROCESSED;
        this.processingLease = BoxProcessingLease.idle();
        this.processedTime = BoxEventTime.present(processedAt);
        this.failedTime = BoxEventTime.absent();
        this.failure = BoxFailureSnapshot.absent();

        return ReviewRequestInboxHistory.completed(
                getId(),
                this.processingAttempt,
                ReviewRequestInboxStatus.PROCESSED,
                processedAt,
                BoxFailureSnapshot.absent()
        );
    }

    public void renewProcessingLease(Instant processingStartedAt) {
        validateProcessingStartedAt(processingStartedAt);
        validateTransition(ReviewRequestInboxStatus.PROCESSING, "PROCESSING");

        this.processingLease = BoxProcessingLease.claimed(processingStartedAt);
    }

    public boolean hasClaimedProcessingLease() {
        return processingLease.isClaimed();
    }

    public boolean hasClaimedProcessingLease(Instant processingStartedAt) {
        if (!hasClaimedProcessingLease()) {
            return false;
        }

        return processingLease.startedAt().equals(processingStartedAt);
    }

    public Instant currentProcessingLeaseStartedAt() {
        if (!hasClaimedProcessingLease()) {
            throw new IllegalStateException("processingLease를 보유하고 있지 않습니다.");
        }

        return processingLease.startedAt();
    }

    public ReviewRequestInboxHistory markRetryPending(Instant failedAt, String failureReason) {
        return markRetryPending(failedAt, failureReason, ReviewRequestInboxFailureType.RETRYABLE);
    }

    public ReviewRequestInboxHistory markRetryPending(
            Instant failedAt,
            String failureReason,
            ReviewRequestInboxFailureType failureType
    ) {
        validateFailedAt(failedAt);
        validateFailureReason(failureReason);
        validateRetryPendingFailureType(failureType);
        validateTransition(ReviewRequestInboxStatus.PROCESSING, "RETRY_PENDING");

        this.status = ReviewRequestInboxStatus.RETRY_PENDING;
        this.processingLease = BoxProcessingLease.idle();
        this.processedTime = BoxEventTime.absent();
        this.failedTime = BoxEventTime.present(failedAt);
        this.failure = BoxFailureSnapshot.present(failureReason, failureType);

        return ReviewRequestInboxHistory.completed(
                getId(),
                this.processingAttempt,
                ReviewRequestInboxStatus.RETRY_PENDING,
                failedAt,
                BoxFailureSnapshot.present(failureReason, failureType)
        );
    }

    public ReviewRequestInboxHistory markFailed(
            Instant failedAt,
            String failureReason,
            ReviewRequestInboxFailureType failureType
    ) {
        validateFailedAt(failedAt);
        validateFailureReason(failureReason);
        validateFailedFailureType(failureType);
        validateTransition(ReviewRequestInboxStatus.PROCESSING, "FAILED");

        this.status = ReviewRequestInboxStatus.FAILED;
        this.processingLease = BoxProcessingLease.idle();
        this.processedTime = BoxEventTime.absent();
        this.failedTime = BoxEventTime.present(failedAt);
        this.failure = BoxFailureSnapshot.present(failureReason, failureType);

        return ReviewRequestInboxHistory.completed(
                getId(),
                this.processingAttempt,
                ReviewRequestInboxStatus.FAILED,
                failedAt,
                BoxFailureSnapshot.present(failureReason, failureType)
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

    private static void validateProcessingLease(BoxProcessingLease processingLease) {
        if (processingLease == null) {
            throw new IllegalArgumentException("processingLease는 비어 있을 수 없습니다.");
        }
    }

    private static void validateProcessedTime(BoxEventTime processedTime) {
        if (processedTime == null) {
            throw new IllegalArgumentException("processedTime은 비어 있을 수 없습니다.");
        }
    }

    private static void validateFailedTime(BoxEventTime failedTime) {
        if (failedTime == null) {
            throw new IllegalArgumentException("failedTime은 비어 있을 수 없습니다.");
        }
    }

    private static void validateFailure(BoxFailureSnapshot<ReviewRequestInboxFailureType> failure) {
        if (failure == null) {
            throw new IllegalArgumentException("failure는 비어 있을 수 없습니다.");
        }
    }

    private static void validateState(
            ReviewRequestInboxStatus status,
            int processingAttempt,
            BoxProcessingLease processingLease,
            BoxEventTime processedTime,
            BoxEventTime failedTime,
            BoxFailureSnapshot<ReviewRequestInboxFailureType> failure
    ) {
        if (status == ReviewRequestInboxStatus.PENDING) {
            validatePendingState(processingAttempt, processingLease, processedTime, failedTime, failure);
            return;
        }
        if (status == ReviewRequestInboxStatus.PROCESSING) {
            validateProcessingState(processingAttempt, processingLease, processedTime, failedTime, failure);
            return;
        }
        if (status == ReviewRequestInboxStatus.PROCESSED) {
            validateProcessedState(processingAttempt, processingLease, processedTime, failedTime, failure);
            return;
        }
        if (status == ReviewRequestInboxStatus.RETRY_PENDING) {
            validateRetryPendingState(processingAttempt, processingLease, processedTime, failedTime, failure);
            return;
        }

        validateFailedState(processingAttempt, processingLease, processedTime, failedTime, failure);
    }

    private static void validatePendingState(
            int processingAttempt,
            BoxProcessingLease processingLease,
            BoxEventTime processedTime,
            BoxEventTime failedTime,
            BoxFailureSnapshot<ReviewRequestInboxFailureType> failure
    ) {
        if (processingAttempt != 0) {
            throw new IllegalArgumentException("PENDING 상태의 processingAttempt는 0이어야 합니다.");
        }
        validateIdleLease(processingLease, "PENDING");
        validateProcessedTimeAbsent(processedTime, "PENDING");
        validateFailedStateAbsent(failedTime, failure, "PENDING");
    }

    private static void validateProcessingState(
            int processingAttempt,
            BoxProcessingLease processingLease,
            BoxEventTime processedTime,
            BoxEventTime failedTime,
            BoxFailureSnapshot<ReviewRequestInboxFailureType> failure
    ) {
        validateProcessedAttemptStarted(processingAttempt, "PROCESSING");
        if (!processingLease.isClaimed()) {
            throw new IllegalArgumentException("PROCESSING 상태는 processingLease를 보유해야 합니다.");
        }
        validateProcessedTimeAbsent(processedTime, "PROCESSING");
        validateFailedStateAbsent(failedTime, failure, "PROCESSING");
    }

    private static void validateProcessedState(
            int processingAttempt,
            BoxProcessingLease processingLease,
            BoxEventTime processedTime,
            BoxEventTime failedTime,
            BoxFailureSnapshot<ReviewRequestInboxFailureType> failure
    ) {
        validateProcessedAttemptStarted(processingAttempt, "PROCESSED");
        validateIdleLease(processingLease, "PROCESSED");
        if (!processedTime.isPresent()) {
            throw new IllegalArgumentException("PROCESSED 상태는 processedTime이 있어야 합니다.");
        }
        validateFailedStateAbsent(failedTime, failure, "PROCESSED");
    }

    private static void validateRetryPendingState(
            int processingAttempt,
            BoxProcessingLease processingLease,
            BoxEventTime processedTime,
            BoxEventTime failedTime,
            BoxFailureSnapshot<ReviewRequestInboxFailureType> failure
    ) {
        validateProcessedAttemptStarted(processingAttempt, "RETRY_PENDING");
        validateIdleLease(processingLease, "RETRY_PENDING");
        validateProcessedTimeAbsent(processedTime, "RETRY_PENDING");
        validateFailedStatePresent(failedTime, failure, "RETRY_PENDING");

        ReviewRequestInboxFailureType failureType = failure.type();
        if (failureType != ReviewRequestInboxFailureType.RETRYABLE
                && failureType != ReviewRequestInboxFailureType.PROCESSING_TIMEOUT) {
            throw new IllegalArgumentException("RETRY_PENDING 상태의 failureType이 올바르지 않습니다.");
        }
    }

    private static void validateFailedState(
            int processingAttempt,
            BoxProcessingLease processingLease,
            BoxEventTime processedTime,
            BoxEventTime failedTime,
            BoxFailureSnapshot<ReviewRequestInboxFailureType> failure
    ) {
        validateProcessedAttemptStarted(processingAttempt, "FAILED");
        validateIdleLease(processingLease, "FAILED");
        validateProcessedTimeAbsent(processedTime, "FAILED");
        validateFailedStatePresent(failedTime, failure, "FAILED");

        ReviewRequestInboxFailureType failureType = failure.type();
        if (failureType != ReviewRequestInboxFailureType.NON_RETRYABLE
                && failureType != ReviewRequestInboxFailureType.RETRY_EXHAUSTED) {
            throw new IllegalArgumentException("FAILED 상태의 failureType이 올바르지 않습니다.");
        }
    }

    private static void validateProcessedAttemptStarted(int processingAttempt, String statusName) {
        if (processingAttempt <= 0) {
            throw new IllegalArgumentException(statusName + " 상태의 processingAttempt는 1 이상이어야 합니다.");
        }
    }

    private static void validateIdleLease(BoxProcessingLease processingLease, String statusName) {
        if (processingLease.isClaimed()) {
            throw new IllegalArgumentException(statusName + " 상태는 processingLease가 비어 있어야 합니다.");
        }
    }

    private static void validateProcessedTimeAbsent(BoxEventTime processedTime, String statusName) {
        if (processedTime.isPresent()) {
            throw new IllegalArgumentException(statusName + " 상태는 processedTime이 비어 있어야 합니다.");
        }
    }

    private static void validateFailedStateAbsent(
            BoxEventTime failedTime,
            BoxFailureSnapshot<ReviewRequestInboxFailureType> failure,
            String statusName
    ) {
        if (failedTime.isPresent()) {
            throw new IllegalArgumentException(statusName + " 상태는 failedTime이 비어 있어야 합니다.");
        }
        if (failure.isPresent()) {
            throw new IllegalArgumentException(statusName + " 상태는 failure가 비어 있어야 합니다.");
        }
    }

    private static void validateFailedStatePresent(
            BoxEventTime failedTime,
            BoxFailureSnapshot<ReviewRequestInboxFailureType> failure,
            String statusName
    ) {
        if (!failedTime.isPresent()) {
            throw new IllegalArgumentException(statusName + " 상태는 failedTime이 있어야 합니다.");
        }
        if (!failure.isPresent()) {
            throw new IllegalArgumentException(statusName + " 상태는 failure가 있어야 합니다.");
        }
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

    private void validateRetryPendingFailureType(ReviewRequestInboxFailureType failureType) {
        if (failureType == ReviewRequestInboxFailureType.RETRYABLE
                || failureType == ReviewRequestInboxFailureType.PROCESSING_TIMEOUT) {
            return;
        }

        throw new IllegalArgumentException("RETRY_PENDING failureType이 올바르지 않습니다.");
    }

    private void validateFailedFailureType(ReviewRequestInboxFailureType failureType) {
        if (failureType == ReviewRequestInboxFailureType.NON_RETRYABLE
                || failureType == ReviewRequestInboxFailureType.RETRY_EXHAUSTED) {
            return;
        }

        throw new IllegalArgumentException("FAILED failureType이 올바르지 않습니다.");
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

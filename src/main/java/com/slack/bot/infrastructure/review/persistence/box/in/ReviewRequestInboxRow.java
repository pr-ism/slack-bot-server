package com.slack.bot.infrastructure.review.persistence.box.in;

import com.slack.bot.infrastructure.common.BoxEventTime;
import com.slack.bot.infrastructure.common.BoxFailureSnapshot;
import com.slack.bot.infrastructure.common.BoxProcessingLease;
import com.slack.bot.infrastructure.review.box.in.ReviewRequestInbox;
import com.slack.bot.infrastructure.review.box.in.ReviewRequestInboxFailureType;
import com.slack.bot.infrastructure.review.box.in.ReviewRequestInboxStatus;
import java.time.Instant;
import lombok.Builder;
import lombok.Getter;

@Getter
public class ReviewRequestInboxRow {

    private Long id;
    private String idempotencyKey;
    private String apiKey;
    private Long githubPullRequestId;
    private String requestJson;
    private Instant availableAt;
    private ReviewRequestInboxStatus status;
    private int processingAttempt;
    private Instant processingStartedAt;
    private Instant processedAt;
    private Instant failedAt;
    private String failureReason;
    private ReviewRequestInboxFailureType failureType;

    @Builder
    public ReviewRequestInboxRow(
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

    public static ReviewRequestInboxRow from(ReviewRequestInbox inbox) {
        Instant processingStartedAt = null;
        if (inbox.getProcessingLease().isClaimed()) {
            processingStartedAt = inbox.getProcessingLease().startedAt();
        }

        Instant processedAt = null;
        if (inbox.getProcessedTime().isPresent()) {
            processedAt = inbox.getProcessedTime().occurredAt();
        }

        Instant failedAt = null;
        if (inbox.getFailedTime().isPresent()) {
            failedAt = inbox.getFailedTime().occurredAt();
        }

        BoxFailureSnapshot<ReviewRequestInboxFailureType> failure = inbox.getFailure();
        String failureReason = null;
        ReviewRequestInboxFailureType failureType = null;
        if (failure.isPresent()) {
            failureReason = failure.reason();
            failureType = failure.type();
        }

        return ReviewRequestInboxRow.builder()
                                    .id(inbox.getId())
                                    .idempotencyKey(inbox.getIdempotencyKey())
                                    .apiKey(inbox.getApiKey())
                                    .githubPullRequestId(inbox.getGithubPullRequestId())
                                    .requestJson(inbox.getRequestJson())
                                    .availableAt(inbox.getAvailableAt())
                                    .status(inbox.getStatus())
                                    .processingAttempt(inbox.getProcessingAttempt())
                                    .processingStartedAt(processingStartedAt)
                                    .processedAt(processedAt)
                                    .failedAt(failedAt)
                                    .failureReason(failureReason)
                                    .failureType(failureType)
                                    .build();
    }

    public ReviewRequestInbox toDomain() {
        return ReviewRequestInbox.rehydrate(
                id,
                idempotencyKey,
                apiKey,
                githubPullRequestId,
                requestJson,
                availableAt,
                status,
                processingAttempt,
                toProcessingLease(),
                toProcessedTime(),
                toFailedTime(),
                toFailure()
        );
    }

    private BoxProcessingLease toProcessingLease() {
        if (processingStartedAt == null) {
            return BoxProcessingLease.idle();
        }

        return BoxProcessingLease.claimed(processingStartedAt);
    }

    private BoxEventTime toProcessedTime() {
        if (processedAt == null) {
            return BoxEventTime.absent();
        }

        return BoxEventTime.present(processedAt);
    }

    private BoxEventTime toFailedTime() {
        if (failedAt == null) {
            return BoxEventTime.absent();
        }

        return BoxEventTime.present(failedAt);
    }

    private BoxFailureSnapshot<ReviewRequestInboxFailureType> toFailure() {
        if (failureReason == null && failureType == null) {
            return BoxFailureSnapshot.absent();
        }
        if (failureReason == null || failureType == null) {
            throw new IllegalStateException("failure 상태가 올바르지 않습니다.");
        }

        return BoxFailureSnapshot.present(failureReason, failureType);
    }
}

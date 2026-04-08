package com.slack.bot.infrastructure.review.persistence.box.in;

import com.slack.bot.domain.common.BaseTimeEntity;
import com.slack.bot.infrastructure.common.BoxEventTime;
import com.slack.bot.infrastructure.common.BoxFailureSnapshot;
import com.slack.bot.infrastructure.common.BoxProcessingLease;
import com.slack.bot.infrastructure.review.box.in.ReviewRequestInbox;
import com.slack.bot.infrastructure.review.box.in.ReviewRequestInboxFailureType;
import com.slack.bot.infrastructure.review.box.in.ReviewRequestInboxStatus;
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
public class ReviewRequestInboxJpaEntity extends BaseTimeEntity {

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

    public ReviewRequestInbox toDomain() {
        return ReviewRequestInbox.rehydrate(
                getId(),
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

    public void apply(ReviewRequestInbox inbox) {
        this.idempotencyKey = inbox.getIdempotencyKey();
        this.apiKey = inbox.getApiKey();
        this.githubPullRequestId = inbox.getGithubPullRequestId();
        this.requestJson = inbox.getRequestJson();
        this.availableAt = inbox.getAvailableAt();
        this.status = inbox.getStatus();
        this.processingAttempt = inbox.getProcessingAttempt();
        applyProcessingLease(inbox);
        applyProcessedTime(inbox);
        applyFailedTime(inbox);
        applyFailure(inbox);
    }

    private void applyProcessingLease(ReviewRequestInbox inbox) {
        this.processingStartedAt = null;
        if (inbox.getProcessingLease().isClaimed()) {
            this.processingStartedAt = inbox.getProcessingLease().startedAt();
        }
    }

    private void applyProcessedTime(ReviewRequestInbox inbox) {
        this.processedAt = null;
        if (inbox.getProcessedTime().isPresent()) {
            this.processedAt = inbox.getProcessedTime().occurredAt();
        }
    }

    private void applyFailedTime(ReviewRequestInbox inbox) {
        this.failedAt = null;
        if (inbox.getFailedTime().isPresent()) {
            this.failedAt = inbox.getFailedTime().occurredAt();
        }
    }

    private void applyFailure(ReviewRequestInbox inbox) {
        this.failureReason = null;
        this.failureType = null;

        BoxFailureSnapshot<ReviewRequestInboxFailureType> failure = inbox.getFailure();
        if (!failure.isPresent()) {
            return;
        }

        this.failureReason = failure.reason();
        this.failureType = failure.type();
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

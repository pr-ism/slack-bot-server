package com.slack.bot.infrastructure.review.persistence.box.in;

import com.slack.bot.domain.common.BaseTimeEntity;
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
                processingStartedAt,
                processedAt,
                failedAt,
                failureReason,
                failureType
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
        this.processingStartedAt = inbox.getProcessingStartedAt();
        this.processedAt = inbox.getProcessedAt();
        this.failedAt = inbox.getFailedAt();
        this.failureReason = inbox.getFailureReason();
        this.failureType = inbox.getFailureType();
    }
}

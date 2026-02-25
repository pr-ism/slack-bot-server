package com.slack.bot.infrastructure.review.box.out;

import com.slack.bot.domain.common.BaseTimeEntity;
import com.slack.bot.infrastructure.interaction.box.SlackInteractivityFailureType;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "review_notification_outbox")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReviewNotificationOutbox extends BaseTimeEntity {

    private String idempotencyKey;

    private String teamId;

    private String channelId;

    private String blocksJson;

    private String fallbackText;

    @Enumerated(EnumType.STRING)
    private ReviewNotificationOutboxStatus status;

    private int processingAttempt;

    private Instant processingStartedAt;

    private Instant sentAt;

    private Instant failedAt;

    private String failureReason;

    @Enumerated(EnumType.STRING)
    private SlackInteractivityFailureType failureType;

    @Builder
    private ReviewNotificationOutbox(
            String idempotencyKey,
            String teamId,
            String channelId,
            String blocksJson,
            String fallbackText
    ) {
        validateIdempotencyKey(idempotencyKey);
        validateTeamId(teamId);
        validateChannelId(channelId);
        validateBlocksJson(blocksJson);

        this.idempotencyKey = idempotencyKey;
        this.teamId = teamId;
        this.channelId = channelId;
        this.blocksJson = blocksJson;
        this.fallbackText = fallbackText;
        this.status = ReviewNotificationOutboxStatus.PENDING;
        this.processingAttempt = 0;
    }

    public void markProcessing(Instant processingStartedAt) {
        validateProcessingStartedAt(processingStartedAt);
        validateProcessingTransition();

        this.status = ReviewNotificationOutboxStatus.PROCESSING;
        this.processingStartedAt = processingStartedAt;
        this.processingAttempt += 1;
        this.failedAt = null;
        this.failureReason = null;
        this.failureType = null;
    }

    public void markSent(Instant sentAt) {
        validateSentAt(sentAt);
        validateTransition(ReviewNotificationOutboxStatus.PROCESSING, "SENT");

        this.status = ReviewNotificationOutboxStatus.SENT;
        this.sentAt = sentAt;
        this.failedAt = null;
        this.failureReason = null;
        this.failureType = null;
    }

    public void markRetryPending(Instant failedAt, String failureReason) {
        validateFailedAt(failedAt);
        validateFailureReason(failureReason);
        validateTransition(ReviewNotificationOutboxStatus.PROCESSING, "RETRY_PENDING");

        this.status = ReviewNotificationOutboxStatus.RETRY_PENDING;
        this.processingStartedAt = null;
        this.failedAt = failedAt;
        this.failureReason = failureReason;
        this.failureType = null;
    }

    public void markFailed(Instant failedAt, String failureReason, SlackInteractivityFailureType failureType) {
        validateFailedAt(failedAt);
        validateFailureReason(failureReason);
        validateFailureType(failureType);
        validateTransition(ReviewNotificationOutboxStatus.PROCESSING, "FAILED");

        this.status = ReviewNotificationOutboxStatus.FAILED;
        this.processingStartedAt = null;
        this.failedAt = failedAt;
        this.failureReason = failureReason;
        this.failureType = failureType;
    }

    private void validateProcessingTransition() {
        if (status == ReviewNotificationOutboxStatus.PENDING || status == ReviewNotificationOutboxStatus.RETRY_PENDING) {
            return;
        }

        throw new IllegalStateException(
                "PROCESSING 전이는 PENDING 또는 RETRY_PENDING 상태에서만 가능합니다. 현재: " + status
        );
    }

    private void validateTransition(ReviewNotificationOutboxStatus expectedStatus, String targetStatus) {
        if (status == expectedStatus) {
            return;
        }

        throw new IllegalStateException(
                targetStatus + " 전이는 " + expectedStatus + " 상태에서만 가능합니다. 현재: " + status
        );
    }

    private void validateIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("idempotencyKey는 비어 있을 수 없습니다.");
        }
    }

    private void validateTeamId(String teamId) {
        if (teamId == null || teamId.isBlank()) {
            throw new IllegalArgumentException("teamId는 비어 있을 수 없습니다.");
        }
    }

    private void validateChannelId(String channelId) {
        if (channelId == null || channelId.isBlank()) {
            throw new IllegalArgumentException("channelId는 비어 있을 수 없습니다.");
        }
    }

    private void validateBlocksJson(String blocksJson) {
        if (blocksJson == null || blocksJson.isBlank()) {
            throw new IllegalArgumentException("blocksJson은 비어 있을 수 없습니다.");
        }
    }

    private void validateProcessingStartedAt(Instant processingStartedAt) {
        if (processingStartedAt == null) {
            throw new IllegalArgumentException("processingStartedAt은 비어 있을 수 없습니다.");
        }
    }

    private void validateSentAt(Instant sentAt) {
        if (sentAt == null) {
            throw new IllegalArgumentException("sentAt은 비어 있을 수 없습니다.");
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
}

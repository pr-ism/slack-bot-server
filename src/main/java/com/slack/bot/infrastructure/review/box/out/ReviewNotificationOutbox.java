package com.slack.bot.infrastructure.review.box.out;

import com.slack.bot.domain.common.BaseTimeEntity;
import com.slack.bot.infrastructure.interaction.box.SlackInteractionFailureType;
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

    private Long projectId;

    private String teamId;

    private String channelId;

    private String payloadJson;

    private String blocksJson;

    private String attachmentsJson;

    private String fallbackText;

    @Enumerated(EnumType.STRING)
    private ReviewNotificationOutboxStatus status;

    private int processingAttempt;

    private Instant processingStartedAt;

    private Instant sentAt;

    private Instant failedAt;

    private String failureReason;

    @Enumerated(EnumType.STRING)
    private SlackInteractionFailureType failureType;

    @Builder
    private ReviewNotificationOutbox(
            String idempotencyKey,
            Long projectId,
            String teamId,
            String channelId,
            String payloadJson,
            String blocksJson,
            String attachmentsJson,
            String fallbackText
    ) {
        validateIdempotencyKey(idempotencyKey);
        validateProjectId(projectId, payloadJson);
        validateTeamId(teamId);
        validateChannelId(channelId);
        validateMessagePayload(payloadJson, blocksJson);

        this.idempotencyKey = idempotencyKey;
        this.projectId = projectId;
        this.teamId = teamId;
        this.channelId = channelId;
        this.payloadJson = payloadJson;
        this.blocksJson = blocksJson;
        this.attachmentsJson = attachmentsJson;
        this.fallbackText = fallbackText;
        this.status = ReviewNotificationOutboxStatus.PENDING;
        this.processingAttempt = 0;
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

    public void markFailed(Instant failedAt, String failureReason, SlackInteractionFailureType failureType) {
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

    private void validateProjectId(Long projectId, String payloadJson) {
        if ((payloadJson == null || payloadJson.isBlank()) && projectId == null) {
            return;
        }

        if (projectId == null || projectId <= 0) {
            throw new IllegalArgumentException("projectId는 1 이상의 값이어야 합니다.");
        }
    }

    private void validateChannelId(String channelId) {
        if (channelId == null || channelId.isBlank()) {
            throw new IllegalArgumentException("channelId는 비어 있을 수 없습니다.");
        }
    }

    private void validateMessagePayload(String payloadJson, String blocksJson) {
        boolean hasSemanticPayload = payloadJson != null && !payloadJson.isBlank();
        boolean hasSnapshotPayload = blocksJson != null && !blocksJson.isBlank();

        if (hasSemanticPayload || hasSnapshotPayload) {
            return;
        }

        throw new IllegalArgumentException("payloadJson 또는 blocksJson 중 하나는 비어 있을 수 없습니다.");
    }

    public boolean hasSemanticPayload() {
        return payloadJson != null && !payloadJson.isBlank();
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

    private void validateFailureType(SlackInteractionFailureType failureType) {
        if (failureType == null) {
            throw new IllegalArgumentException("failureType은 비어 있을 수 없습니다.");
        }
    }
}

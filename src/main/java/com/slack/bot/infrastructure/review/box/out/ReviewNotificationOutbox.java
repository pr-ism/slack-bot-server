package com.slack.bot.infrastructure.review.box.out;

import com.slack.bot.domain.common.BaseTimeEntity;
import com.slack.bot.infrastructure.common.FailureSnapshotDefaults;
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
        this.processingStartedAt = FailureSnapshotDefaults.NO_PROCESSING_STARTED_AT;
        this.sentAt = FailureSnapshotDefaults.NO_SENT_AT;
        this.failedAt = FailureSnapshotDefaults.NO_FAILURE_AT;
        this.failureReason = FailureSnapshotDefaults.NO_FAILURE_REASON;
        this.failureType = SlackInteractionFailureType.NONE;
    }

    public ReviewNotificationOutboxHistory markSent(Instant sentAt) {
        validateSentAt(sentAt);
        validateTransition(ReviewNotificationOutboxStatus.PROCESSING, "SENT");

        this.status = ReviewNotificationOutboxStatus.SENT;
        this.processingStartedAt = FailureSnapshotDefaults.NO_PROCESSING_STARTED_AT;
        this.sentAt = sentAt;
        this.failedAt = FailureSnapshotDefaults.NO_FAILURE_AT;
        this.failureReason = FailureSnapshotDefaults.NO_FAILURE_REASON;
        this.failureType = SlackInteractionFailureType.NONE;

        return ReviewNotificationOutboxHistory.completed(
                getId(),
                this.processingAttempt,
                ReviewNotificationOutboxStatus.SENT,
                sentAt,
                FailureSnapshotDefaults.NO_FAILURE_REASON,
                SlackInteractionFailureType.NONE
        );
    }

    public void renewProcessingLease(Instant processingStartedAt) {
        validateProcessingStartedAt(processingStartedAt);
        validateTransition(ReviewNotificationOutboxStatus.PROCESSING, "PROCESSING");

        this.processingStartedAt = processingStartedAt;
    }

    public ReviewNotificationOutboxHistory markRetryPending(Instant failedAt, String failureReason) {
        validateFailedAt(failedAt);
        validateFailureReason(failureReason);
        validateTransition(ReviewNotificationOutboxStatus.PROCESSING, "RETRY_PENDING");

        this.status = ReviewNotificationOutboxStatus.RETRY_PENDING;
        this.processingStartedAt = FailureSnapshotDefaults.NO_PROCESSING_STARTED_AT;
        this.sentAt = FailureSnapshotDefaults.NO_SENT_AT;
        this.failedAt = failedAt;
        this.failureReason = failureReason;
        this.failureType = SlackInteractionFailureType.NONE;

        return ReviewNotificationOutboxHistory.completed(
                getId(),
                this.processingAttempt,
                ReviewNotificationOutboxStatus.RETRY_PENDING,
                failedAt,
                failureReason,
                SlackInteractionFailureType.NONE
        );
    }

    public ReviewNotificationOutboxHistory markFailed(
            Instant failedAt,
            String failureReason,
            SlackInteractionFailureType failureType
    ) {
        validateFailedAt(failedAt);
        validateFailureReason(failureReason);
        validateFailureType(failureType);
        validateTransition(ReviewNotificationOutboxStatus.PROCESSING, "FAILED");

        this.status = ReviewNotificationOutboxStatus.FAILED;
        this.processingStartedAt = FailureSnapshotDefaults.NO_PROCESSING_STARTED_AT;
        this.sentAt = FailureSnapshotDefaults.NO_SENT_AT;
        this.failedAt = failedAt;
        this.failureReason = failureReason;
        this.failureType = failureType;

        return ReviewNotificationOutboxHistory.completed(
                getId(),
                this.processingAttempt,
                ReviewNotificationOutboxStatus.FAILED,
                failedAt,
                failureReason,
                failureType
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

    private void validateFailureType(SlackInteractionFailureType failureType) {
        if (failureType == null || failureType == SlackInteractionFailureType.NONE) {
            throw new IllegalArgumentException("failureType은 NONE일 수 없습니다.");
        }
    }
}

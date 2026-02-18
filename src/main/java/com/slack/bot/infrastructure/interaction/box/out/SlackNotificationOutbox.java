package com.slack.bot.infrastructure.interaction.box.out;

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
@Table(name = "slack_notification_outbox")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SlackNotificationOutbox extends BaseTimeEntity {

    @Enumerated(EnumType.STRING)
    private SlackNotificationOutboxMessageType messageType;

    private String idempotencyKey;

    private String teamId;

    private String channelId;

    private String userId;

    private String text;

    private String blocksJson;

    private String fallbackText;

    @Enumerated(EnumType.STRING)
    private SlackNotificationOutboxStatus status;

    private int processingAttempt;

    private Instant processingStartedAt;

    private Instant sentAt;

    private Instant failedAt;

    private String failureReason;

    @Enumerated(EnumType.STRING)
    private SlackInteractivityFailureType failureType;

    @Builder
    private SlackNotificationOutbox(
            SlackNotificationOutboxMessageType messageType,
            String idempotencyKey,
            String teamId,
            String channelId,
            String userId,
            String text,
            String blocksJson,
            String fallbackText
    ) {
        validateMessageType(messageType);
        validateIdempotencyKey(idempotencyKey);
        validateTeamId(teamId);
        validateChannelId(channelId);

        this.messageType = messageType;
        this.idempotencyKey = idempotencyKey;
        this.teamId = teamId;
        this.channelId = channelId;
        this.userId = userId;
        this.text = text;
        this.blocksJson = blocksJson;
        this.fallbackText = fallbackText;
        this.status = SlackNotificationOutboxStatus.PENDING;
        this.processingAttempt = 0;
    }

    public void markProcessing(Instant processingStartedAt) {
        validateProcessingTransition();
        validateProcessingStartedAt(processingStartedAt);

        this.status = SlackNotificationOutboxStatus.PROCESSING;
        this.processingStartedAt = processingStartedAt;
        this.processingAttempt += 1;
        this.failedAt = null;
        this.failureReason = null;
        this.failureType = null;
    }

    public void markSent(Instant sentAt) {
        validateTransition(SlackNotificationOutboxStatus.PROCESSING, "SENT");
        validateSentAt(sentAt);

        this.status = SlackNotificationOutboxStatus.SENT;
        this.sentAt = sentAt;
        this.failedAt = null;
        this.failureReason = null;
        this.failureType = null;
    }

    public void markRetryPending(Instant failedAt, String failureReason) {
        validateTransition(SlackNotificationOutboxStatus.PROCESSING, "RETRY_PENDING");
        validateFailedAt(failedAt);

        this.status = SlackNotificationOutboxStatus.RETRY_PENDING;
        this.processingStartedAt = null;
        this.failedAt = failedAt;
        this.failureReason = failureReason;
        this.failureType = null;
    }

    public void markFailed(
            Instant failedAt,
            String failureReason,
            SlackInteractivityFailureType failureType
    ) {
        validateTransition(SlackNotificationOutboxStatus.PROCESSING, "FAILED");
        validateFailedAt(failedAt);
        validateFailureType(failureType);

        this.status = SlackNotificationOutboxStatus.FAILED;
        this.failedAt = failedAt;
        this.failureReason = failureReason;
        this.failureType = failureType;
    }

    private void validateProcessingTransition() {
        if (isPendingOrRetryPendingStatus()) {
            return;
        }

        throw new IllegalStateException(
                "PROCESSING 전이는 PENDING 또는 RETRY_PENDING 상태에서만 가능합니다. 현재: " + this.status
        );
    }

    private boolean isPendingOrRetryPendingStatus() {
        return this.status == SlackNotificationOutboxStatus.PENDING
                || this.status == SlackNotificationOutboxStatus.RETRY_PENDING;
    }

    private void validateTransition(SlackNotificationOutboxStatus expected, String targetStatus) {
        if (this.status == expected) {
            return;
        }

        throw new IllegalStateException(
                targetStatus + " 전이는 " + expected + " 상태에서만 가능합니다. 현재: " + this.status
        );
    }

    private void validateMessageType(SlackNotificationOutboxMessageType messageType) {
        if (messageType == null) {
            throw new IllegalArgumentException("outbox messageType은 비어 있을 수 없습니다.");
        }
    }

    private void validateIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("outbox idempotencyKey는 비어 있을 수 없습니다.");
        }
    }

    private void validateTeamId(String teamId) {
        if (teamId == null || teamId.isBlank()) {
            throw new IllegalArgumentException("outbox teamId는 비어 있을 수 없습니다.");
        }
    }

    private void validateChannelId(String channelId) {
        if (channelId == null || channelId.isBlank()) {
            throw new IllegalArgumentException("outbox channelId는 비어 있을 수 없습니다.");
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

    private void validateFailureType(SlackInteractivityFailureType failureType) {
        if (failureType == null) {
            throw new IllegalArgumentException("failureType은 비어 있을 수 없습니다.");
        }
    }
}

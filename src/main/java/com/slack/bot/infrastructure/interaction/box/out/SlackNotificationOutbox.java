package com.slack.bot.infrastructure.interaction.box.out;

import com.slack.bot.domain.common.BaseTimeEntity;
import com.slack.bot.infrastructure.interaction.box.SlackInteractivityFailureType;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
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
        this.messageType = Objects.requireNonNull(messageType, "outbox messageType은 비어 있을 수 없습니다.");
        this.idempotencyKey = requireNonBlank(idempotencyKey, "outbox idempotencyKey는 비어 있을 수 없습니다.");
        this.teamId = requireNonBlank(teamId, "outbox teamId는 비어 있을 수 없습니다.");
        this.channelId = requireNonBlank(channelId, "outbox channelId는 비어 있을 수 없습니다.");
        this.userId = userId;
        this.text = text;
        this.blocksJson = blocksJson;
        this.fallbackText = fallbackText;
        this.status = SlackNotificationOutboxStatus.PENDING;
        this.processingAttempt = 0;
    }

    public void markProcessing(Instant processingStartedAt) {
        validateProcessingTransition();
        Objects.requireNonNull(processingStartedAt, "processingStartedAt은 비어 있을 수 없습니다.");

        this.status = SlackNotificationOutboxStatus.PROCESSING;
        this.processingStartedAt = processingStartedAt;
        this.processingAttempt += 1;
    }

    public void markSent(Instant sentAt) {
        validateTransition(SlackNotificationOutboxStatus.PROCESSING, "SENT");
        Objects.requireNonNull(sentAt, "sentAt은 비어 있을 수 없습니다.");

        this.status = SlackNotificationOutboxStatus.SENT;
        this.sentAt = sentAt;
        this.failedAt = null;
        this.failureReason = null;
        this.failureType = null;
    }

    public void markRetryPending(Instant failedAt, String failureReason) {
        validateTransition(SlackNotificationOutboxStatus.PROCESSING, "RETRY_PENDING");
        Objects.requireNonNull(failedAt, "failedAt은 비어 있을 수 없습니다.");

        this.status = SlackNotificationOutboxStatus.RETRY_PENDING;
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
        Objects.requireNonNull(failedAt, "failedAt은 비어 있을 수 없습니다.");
        Objects.requireNonNull(failureType, "failureType은 비어 있을 수 없습니다.");

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

    private String requireNonBlank(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }

        return value;
    }
}

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

    private String token;

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
            String token,
            String channelId,
            String userId,
            String text,
            String blocksJson,
            String fallbackText
    ) {
        this.messageType = messageType;
        this.idempotencyKey = idempotencyKey;
        this.token = token;
        this.channelId = channelId;
        this.userId = userId;
        this.text = text;
        this.blocksJson = blocksJson;
        this.fallbackText = fallbackText;
        this.status = SlackNotificationOutboxStatus.PENDING;
        this.processingAttempt = 0;
    }

    public void markProcessing(Instant processingStartedAt) {
        this.status = SlackNotificationOutboxStatus.PROCESSING;
        this.processingStartedAt = processingStartedAt;
        this.processingAttempt += 1;
    }

    public void markSent(Instant sentAt) {
        this.status = SlackNotificationOutboxStatus.SENT;
        this.sentAt = sentAt;
        this.failedAt = null;
        this.failureReason = null;
        this.failureType = null;
    }

    public void markRetryPending(Instant failedAt, String failureReason) {
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
        this.status = SlackNotificationOutboxStatus.FAILED;
        this.failedAt = failedAt;
        this.failureReason = failureReason;
        this.failureType = failureType;
    }
}

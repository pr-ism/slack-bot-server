package com.slack.bot.infrastructure.interaction.box.in;

import com.slack.bot.domain.common.BaseTimeEntity;
import com.slack.bot.infrastructure.interaction.box.SlackInteractivityFailureType;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "slack_interaction_inbox")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SlackInteractionInbox extends BaseTimeEntity {

    @Enumerated(EnumType.STRING)
    private SlackInteractionInboxType interactionType;

    private String idempotencyKey;

    private String payloadJson;

    @Enumerated(EnumType.STRING)
    private SlackInteractionInboxStatus status;

    private int processingAttempt;

    private Instant processingStartedAt;

    private Instant processedAt;

    private Instant failedAt;

    private String failureReason;

    @Enumerated(EnumType.STRING)
    private SlackInteractivityFailureType failureType;

    public static SlackInteractionInbox pending(
            SlackInteractionInboxType interactionType,
            String idempotencyKey,
            String payloadJson
    ) {
        return new SlackInteractionInbox(interactionType, idempotencyKey, payloadJson);
    }

    private SlackInteractionInbox(
            SlackInteractionInboxType interactionType,
            String idempotencyKey,
            String payloadJson
    ) {
        this.interactionType = interactionType;
        this.idempotencyKey = idempotencyKey;
        this.payloadJson = payloadJson;
        this.status = SlackInteractionInboxStatus.PENDING;
        this.processingAttempt = 0;
    }

    public void markProcessing(Instant processingStartedAt) {
        Objects.requireNonNull(processingStartedAt, "processingStartedAt은 비어 있을 수 없습니다.");

        this.status = SlackInteractionInboxStatus.PROCESSING;
        this.processingAttempt += 1;
        this.processingStartedAt = processingStartedAt;
        this.failureReason = null;
        this.failureType = null;
        this.failedAt = null;
    }

    public void markProcessed(Instant processedAt) {
        this.status = SlackInteractionInboxStatus.PROCESSED;
        this.processingStartedAt = null;
        this.processedAt = processedAt;
        this.failureReason = null;
        this.failureType = null;
        this.failedAt = null;
    }

    public void markRetryPending(Instant failedAt, String failureReason) {
        this.status = SlackInteractionInboxStatus.RETRY_PENDING;
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
        this.status = SlackInteractionInboxStatus.FAILED;
        this.processingStartedAt = null;
        this.failedAt = failedAt;
        this.failureReason = failureReason;
        this.failureType = failureType;
    }
}

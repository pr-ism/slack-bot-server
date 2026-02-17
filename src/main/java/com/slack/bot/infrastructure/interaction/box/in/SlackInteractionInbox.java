package com.slack.bot.infrastructure.interaction.box.in;

import com.slack.bot.domain.common.BaseTimeEntity;
import com.slack.bot.infrastructure.interaction.box.SlackInteractivityFailureType;
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
        validateInteractionType(interactionType);
        validateIdempotencyKey(idempotencyKey);
        validatePayloadJson(payloadJson);

        return new SlackInteractionInbox(
                interactionType,
                idempotencyKey,
                payloadJson,
                SlackInteractionInboxStatus.PENDING,
                0
        );
    }

    private static void validateInteractionType(SlackInteractionInboxType interactionType) {
        if (interactionType == null) {
            throw new IllegalArgumentException("interactionType은 비어 있을 수 없습니다.");
        }
    }

    private static void validateIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("idempotencyKey는 비어 있을 수 없습니다.");
        }
    }

    private static void validatePayloadJson(String payloadJson) {
        if (payloadJson == null || payloadJson.isBlank()) {
            throw new IllegalArgumentException("payloadJson은 비어 있을 수 없습니다.");
        }
    }

    private SlackInteractionInbox(
            SlackInteractionInboxType interactionType,
            String idempotencyKey,
            String payloadJson,
            SlackInteractionInboxStatus status,
            int processingAttempt
    ) {
        this.interactionType = interactionType;
        this.idempotencyKey = idempotencyKey;
        this.payloadJson = payloadJson;
        this.status = status;
        this.processingAttempt = processingAttempt;
    }

    public void markProcessing(Instant processingStartedAt) {
        validateProcessingStartedAt(processingStartedAt);

        this.status = SlackInteractionInboxStatus.PROCESSING;
        this.processingAttempt += 1;
        this.processingStartedAt = processingStartedAt;
        this.failureReason = null;
        this.failureType = null;
        this.failedAt = null;
    }

    public void markProcessed(Instant processedAt) {
        validateProcessedAt(processedAt);

        this.status = SlackInteractionInboxStatus.PROCESSED;
        this.processingStartedAt = null;
        this.processedAt = processedAt;
        this.failureReason = null;
        this.failureType = null;
        this.failedAt = null;
    }

    public void markRetryPending(Instant failedAt, String failureReason) {
        validateFailedAt(failedAt);
        validateFailureReason(failureReason);

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
        validateFailedAt(failedAt);
        validateFailureReason(failureReason);
        validateFailureType(failureType);

        this.status = SlackInteractionInboxStatus.FAILED;
        this.processingStartedAt = null;
        this.failedAt = failedAt;
        this.failureReason = failureReason;
        this.failureType = failureType;
    }

    private void validateProcessingStartedAt(Instant processingStartedAt) {
        if (processingStartedAt == null) {
            throw new IllegalArgumentException("processingStartedAt은 비어 있을 수 없습니다.");
        }
    }

    private void validateProcessedAt(Instant processedAt) {
        if (processedAt == null) {
            throw new IllegalArgumentException("processedAt은 비어 있을 수 없습니다.");
        }
    }

    private void validateFailedAt(Instant failedAt) {
        if (failedAt == null) {
            throw new IllegalArgumentException("failedAt은 비어 있을 수 없습니다.");
        }
    }

    private void validateFailureReason(String failureReason) {
        if (failureReason == null) {
            throw new IllegalArgumentException("failureReason은 비어 있을 수 없습니다.");
        }
    }

    private void validateFailureType(SlackInteractivityFailureType failureType) {
        if (failureType == null) {
            throw new IllegalArgumentException("failureType은 비어 있을 수 없습니다.");
        }
    }
}

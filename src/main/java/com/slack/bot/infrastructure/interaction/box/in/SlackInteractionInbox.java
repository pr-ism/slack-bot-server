package com.slack.bot.infrastructure.interaction.box.in;

import com.slack.bot.domain.common.BaseTimeEntity;
import com.slack.bot.infrastructure.common.FailureSnapshotDefaults;
import com.slack.bot.infrastructure.interaction.box.SlackInteractionFailureType;
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
    private SlackInteractionFailureType failureType;

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
        this.processingStartedAt = FailureSnapshotDefaults.NO_PROCESSING_STARTED_AT;
        this.processedAt = FailureSnapshotDefaults.NO_PROCESSED_AT;
        this.failedAt = FailureSnapshotDefaults.NO_FAILURE_AT;
        this.failureReason = FailureSnapshotDefaults.NO_FAILURE_REASON;
        this.failureType = SlackInteractionFailureType.NONE;
    }

    public SlackInteractionInboxHistory markProcessed(Instant processedAt) {
        validateProcessedAt(processedAt);
        validateTransition(SlackInteractionInboxStatus.PROCESSING, "PROCESSED");

        this.status = SlackInteractionInboxStatus.PROCESSED;
        this.processingStartedAt = FailureSnapshotDefaults.NO_PROCESSING_STARTED_AT;
        this.processedAt = processedAt;
        this.failedAt = FailureSnapshotDefaults.NO_FAILURE_AT;
        this.failureReason = FailureSnapshotDefaults.NO_FAILURE_REASON;
        this.failureType = SlackInteractionFailureType.NONE;

        return SlackInteractionInboxHistory.completed(
                getId(),
                this.processingAttempt,
                SlackInteractionInboxStatus.PROCESSED,
                processedAt,
                FailureSnapshotDefaults.NO_FAILURE_REASON,
                SlackInteractionFailureType.NONE
        );
    }

    public SlackInteractionInboxHistory markRetryPending(Instant failedAt, String failureReason) {
        validateFailedAt(failedAt);
        validateFailureReason(failureReason);
        validateTransition(SlackInteractionInboxStatus.PROCESSING, "RETRY_PENDING");

        this.status = SlackInteractionInboxStatus.RETRY_PENDING;
        this.processingStartedAt = FailureSnapshotDefaults.NO_PROCESSING_STARTED_AT;
        this.processedAt = FailureSnapshotDefaults.NO_PROCESSED_AT;
        this.failedAt = failedAt;
        this.failureReason = failureReason;
        this.failureType = SlackInteractionFailureType.NONE;

        return SlackInteractionInboxHistory.completed(
                getId(),
                this.processingAttempt,
                SlackInteractionInboxStatus.RETRY_PENDING,
                failedAt,
                failureReason,
                SlackInteractionFailureType.NONE
        );
    }

    public SlackInteractionInboxHistory markFailed(
            Instant failedAt,
            String failureReason,
            SlackInteractionFailureType failureType
    ) {
        validateFailedAt(failedAt);
        validateFailureReason(failureReason);
        validateFailureType(failureType);
        validateTransition(SlackInteractionInboxStatus.PROCESSING, "FAILED");

        this.status = SlackInteractionInboxStatus.FAILED;
        this.processingStartedAt = FailureSnapshotDefaults.NO_PROCESSING_STARTED_AT;
        this.processedAt = FailureSnapshotDefaults.NO_PROCESSED_AT;
        this.failedAt = failedAt;
        this.failureReason = failureReason;
        this.failureType = failureType;

        return SlackInteractionInboxHistory.completed(
                getId(),
                this.processingAttempt,
                SlackInteractionInboxStatus.FAILED,
                failedAt,
                failureReason,
                failureType
        );
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
        if (failureReason == null || failureReason.isBlank()) {
            throw new IllegalArgumentException("failureReason은 비어 있을 수 없습니다.");
        }
    }

    private void validateFailureType(SlackInteractionFailureType failureType) {
        if (failureType == null || failureType == SlackInteractionFailureType.NONE) {
            throw new IllegalArgumentException("failureType은 NONE일 수 없습니다.");
        }
    }

    private void validateTransition(SlackInteractionInboxStatus expectedStatus, String targetStatus) {
        if (this.status == expectedStatus) {
            return;
        }

        throw new IllegalStateException(
                targetStatus + " 전이는 " + expectedStatus + " 상태에서만 가능합니다. 현재: " + this.status
        );
    }
}

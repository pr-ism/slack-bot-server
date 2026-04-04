package com.slack.bot.infrastructure.interaction.box.in;

import com.slack.bot.infrastructure.common.BoxEventTime;
import com.slack.bot.infrastructure.common.BoxFailureSnapshot;
import com.slack.bot.infrastructure.common.BoxProcessingLease;
import com.slack.bot.infrastructure.interaction.box.SlackInteractionFailureType;
import java.time.Instant;
import lombok.Getter;

@Getter
public class SlackInteractionInbox {

    private final Long id;
    private final SlackInteractionInboxType interactionType;
    private final String idempotencyKey;
    private final String payloadJson;

    private SlackInteractionInboxStatus status;
    private int processingAttempt;
    private BoxProcessingLease processingLease;
    private BoxEventTime processedTime;
    private BoxEventTime failedTime;
    private BoxFailureSnapshot<SlackInteractionFailureType> failure;

    public static SlackInteractionInbox pending(
            SlackInteractionInboxType interactionType,
            String idempotencyKey,
            String payloadJson
    ) {
        validateInteractionType(interactionType);
        validateIdempotencyKey(idempotencyKey);
        validatePayloadJson(payloadJson);

        return new SlackInteractionInbox(
                null,
                interactionType,
                idempotencyKey,
                payloadJson,
                SlackInteractionInboxStatus.PENDING,
                0,
                BoxProcessingLease.idle(),
                BoxEventTime.absent(),
                BoxEventTime.absent(),
                BoxFailureSnapshot.absent()
        );
    }

    public static SlackInteractionInbox rehydrate(
            Long id,
            SlackInteractionInboxType interactionType,
            String idempotencyKey,
            String payloadJson,
            SlackInteractionInboxStatus status,
            int processingAttempt,
            BoxProcessingLease processingLease,
            BoxEventTime processedTime,
            BoxEventTime failedTime,
            BoxFailureSnapshot<SlackInteractionFailureType> failure
    ) {
        validateInteractionType(interactionType);
        validateIdempotencyKey(idempotencyKey);
        validatePayloadJson(payloadJson);
        validateStatus(status);
        validateProcessingAttempt(processingAttempt);
        validateProcessingLease(processingLease);
        validateProcessedTime(processedTime);
        validateFailedTime(failedTime);
        validateFailure(failure);
        validateState(
                status,
                processingAttempt,
                processingLease,
                processedTime,
                failedTime,
                failure
        );

        return new SlackInteractionInbox(
                id,
                interactionType,
                idempotencyKey,
                payloadJson,
                status,
                processingAttempt,
                processingLease,
                processedTime,
                failedTime,
                failure
        );
    }

    private SlackInteractionInbox(
            Long id,
            SlackInteractionInboxType interactionType,
            String idempotencyKey,
            String payloadJson,
            SlackInteractionInboxStatus status,
            int processingAttempt,
            BoxProcessingLease processingLease,
            BoxEventTime processedTime,
            BoxEventTime failedTime,
            BoxFailureSnapshot<SlackInteractionFailureType> failure
    ) {
        this.id = id;
        this.interactionType = interactionType;
        this.idempotencyKey = idempotencyKey;
        this.payloadJson = payloadJson;
        this.status = status;
        this.processingAttempt = processingAttempt;
        this.processingLease = processingLease;
        this.processedTime = processedTime;
        this.failedTime = failedTime;
        this.failure = failure;
    }

    public SlackInteractionInboxHistory markProcessed(Instant processedAt) {
        validateProcessedAt(processedAt);
        validateTransition(SlackInteractionInboxStatus.PROCESSING, "PROCESSED");

        this.status = SlackInteractionInboxStatus.PROCESSED;
        this.processingLease = BoxProcessingLease.idle();
        this.processedTime = BoxEventTime.present(processedAt);
        this.failedTime = BoxEventTime.absent();
        this.failure = BoxFailureSnapshot.absent();

        return SlackInteractionInboxHistory.completed(
                getId(),
                this.processingAttempt,
                SlackInteractionInboxStatus.PROCESSED,
                processedAt,
                BoxFailureSnapshot.absent()
        );
    }

    public void renewProcessingLease(Instant processingStartedAt) {
        validateProcessingStartedAt(processingStartedAt);
        validateTransition(SlackInteractionInboxStatus.PROCESSING, "PROCESSING");

        this.processingLease = BoxProcessingLease.claimed(processingStartedAt);
    }

    public void claim(Instant processingStartedAt) {
        validateProcessingStartedAt(processingStartedAt);
        validateClaimableStatus();

        this.status = SlackInteractionInboxStatus.PROCESSING;
        this.processingAttempt++;
        this.processingLease = BoxProcessingLease.claimed(processingStartedAt);
        this.processedTime = BoxEventTime.absent();
        this.failedTime = BoxEventTime.absent();
        this.failure = BoxFailureSnapshot.absent();
    }

    public SlackInteractionInboxHistory markRetryPending(Instant failedAt, String failureReason) {
        return markRetryPending(failedAt, failureReason, SlackInteractionFailureType.RETRYABLE);
    }

    public SlackInteractionInboxHistory markRetryPending(
            Instant failedAt,
            String failureReason,
            SlackInteractionFailureType failureType
    ) {
        validateFailedAt(failedAt);
        validateFailureReason(failureReason);
        validateRetryPendingFailureType(failureType);
        validateTransition(SlackInteractionInboxStatus.PROCESSING, "RETRY_PENDING");

        this.status = SlackInteractionInboxStatus.RETRY_PENDING;
        this.processingLease = BoxProcessingLease.idle();
        this.processedTime = BoxEventTime.absent();
        this.failedTime = BoxEventTime.present(failedAt);
        this.failure = BoxFailureSnapshot.present(failureReason, failureType);

        return SlackInteractionInboxHistory.completed(
                getId(),
                this.processingAttempt,
                SlackInteractionInboxStatus.RETRY_PENDING,
                failedAt,
                BoxFailureSnapshot.present(failureReason, failureType)
        );
    }

    public SlackInteractionInboxHistory markFailed(
            Instant failedAt,
            String failureReason,
            SlackInteractionFailureType failureType
    ) {
        validateFailedAt(failedAt);
        validateFailureReason(failureReason);
        validateFailedFailureType(failureType);
        validateTransition(SlackInteractionInboxStatus.PROCESSING, "FAILED");

        this.status = SlackInteractionInboxStatus.FAILED;
        this.processingLease = BoxProcessingLease.idle();
        this.processedTime = BoxEventTime.absent();
        this.failedTime = BoxEventTime.present(failedAt);
        this.failure = BoxFailureSnapshot.present(failureReason, failureType);

        return SlackInteractionInboxHistory.completed(
                getId(),
                this.processingAttempt,
                SlackInteractionInboxStatus.FAILED,
                failedAt,
                BoxFailureSnapshot.present(failureReason, failureType)
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

    private static void validateStatus(SlackInteractionInboxStatus status) {
        if (status == null) {
            throw new IllegalArgumentException("status는 비어 있을 수 없습니다.");
        }
    }

    private static void validateProcessingAttempt(int processingAttempt) {
        if (processingAttempt < 0) {
            throw new IllegalArgumentException("processingAttempt는 0 이상이어야 합니다.");
        }
    }

    private static void validateProcessingLease(BoxProcessingLease processingLease) {
        if (processingLease == null) {
            throw new IllegalArgumentException("processingLease는 비어 있을 수 없습니다.");
        }
    }

    private static void validateProcessedTime(BoxEventTime processedTime) {
        if (processedTime == null) {
            throw new IllegalArgumentException("processedTime은 비어 있을 수 없습니다.");
        }
    }

    private static void validateFailedTime(BoxEventTime failedTime) {
        if (failedTime == null) {
            throw new IllegalArgumentException("failedTime은 비어 있을 수 없습니다.");
        }
    }

    private static void validateFailure(BoxFailureSnapshot<SlackInteractionFailureType> failure) {
        if (failure == null) {
            throw new IllegalArgumentException("failure는 비어 있을 수 없습니다.");
        }
    }

    private static void validateState(
            SlackInteractionInboxStatus status,
            int processingAttempt,
            BoxProcessingLease processingLease,
            BoxEventTime processedTime,
            BoxEventTime failedTime,
            BoxFailureSnapshot<SlackInteractionFailureType> failure
    ) {
        if (status == SlackInteractionInboxStatus.PENDING) {
            validatePendingState(processingAttempt, processingLease, processedTime, failedTime, failure);
            return;
        }
        if (status == SlackInteractionInboxStatus.PROCESSING) {
            validateProcessingState(processingAttempt, processingLease, processedTime, failedTime, failure);
            return;
        }
        if (status == SlackInteractionInboxStatus.PROCESSED) {
            validateProcessedState(processingAttempt, processingLease, processedTime, failedTime, failure);
            return;
        }
        if (status == SlackInteractionInboxStatus.RETRY_PENDING) {
            validateRetryPendingState(processingAttempt, processingLease, processedTime, failedTime, failure);
            return;
        }

        validateFailedState(processingAttempt, processingLease, processedTime, failedTime, failure);
    }

    private static void validatePendingState(
            int processingAttempt,
            BoxProcessingLease processingLease,
            BoxEventTime processedTime,
            BoxEventTime failedTime,
            BoxFailureSnapshot<SlackInteractionFailureType> failure
    ) {
        if (processingAttempt != 0) {
            throw new IllegalArgumentException("PENDING 상태의 processingAttempt는 0이어야 합니다.");
        }
        validateIdleLease(processingLease, "PENDING");
        validateProcessedTimeAbsent(processedTime, "PENDING");
        validateFailedStateAbsent(failedTime, failure, "PENDING");
    }

    private static void validateProcessingState(
            int processingAttempt,
            BoxProcessingLease processingLease,
            BoxEventTime processedTime,
            BoxEventTime failedTime,
            BoxFailureSnapshot<SlackInteractionFailureType> failure
    ) {
        validateProcessedAttemptStarted(processingAttempt, "PROCESSING");
        if (!processingLease.isClaimed()) {
            throw new IllegalArgumentException("PROCESSING 상태는 processingLease를 보유해야 합니다.");
        }
        validateProcessedTimeAbsent(processedTime, "PROCESSING");
        validateFailedStateAbsent(failedTime, failure, "PROCESSING");
    }

    private static void validateProcessedState(
            int processingAttempt,
            BoxProcessingLease processingLease,
            BoxEventTime processedTime,
            BoxEventTime failedTime,
            BoxFailureSnapshot<SlackInteractionFailureType> failure
    ) {
        validateProcessedAttemptStarted(processingAttempt, "PROCESSED");
        validateIdleLease(processingLease, "PROCESSED");
        if (!processedTime.isPresent()) {
            throw new IllegalArgumentException("PROCESSED 상태는 processedTime이 있어야 합니다.");
        }
        validateFailedStateAbsent(failedTime, failure, "PROCESSED");
    }

    private static void validateRetryPendingState(
            int processingAttempt,
            BoxProcessingLease processingLease,
            BoxEventTime processedTime,
            BoxEventTime failedTime,
            BoxFailureSnapshot<SlackInteractionFailureType> failure
    ) {
        validateProcessedAttemptStarted(processingAttempt, "RETRY_PENDING");
        validateIdleLease(processingLease, "RETRY_PENDING");
        validateProcessedTimeAbsent(processedTime, "RETRY_PENDING");
        validateFailedStatePresent(failedTime, failure, "RETRY_PENDING");

        SlackInteractionFailureType failureType = failure.type();
        if (failureType != SlackInteractionFailureType.RETRYABLE
                && failureType != SlackInteractionFailureType.PROCESSING_TIMEOUT) {
            throw new IllegalArgumentException("RETRY_PENDING 상태의 failureType이 올바르지 않습니다.");
        }
    }

    private static void validateFailedState(
            int processingAttempt,
            BoxProcessingLease processingLease,
            BoxEventTime processedTime,
            BoxEventTime failedTime,
            BoxFailureSnapshot<SlackInteractionFailureType> failure
    ) {
        validateProcessedAttemptStarted(processingAttempt, "FAILED");
        validateIdleLease(processingLease, "FAILED");
        validateProcessedTimeAbsent(processedTime, "FAILED");
        validateFailedStatePresent(failedTime, failure, "FAILED");

        SlackInteractionFailureType failureType = failure.type();
        if (failureType != SlackInteractionFailureType.BUSINESS_INVARIANT
                && failureType != SlackInteractionFailureType.RETRY_EXHAUSTED) {
            throw new IllegalArgumentException("FAILED 상태의 failureType이 올바르지 않습니다.");
        }
    }

    private static void validateProcessedAttemptStarted(int processingAttempt, String statusName) {
        if (processingAttempt <= 0) {
            throw new IllegalArgumentException(statusName + " 상태의 processingAttempt는 1 이상이어야 합니다.");
        }
    }

    private static void validateIdleLease(BoxProcessingLease processingLease, String statusName) {
        if (processingLease.isClaimed()) {
            throw new IllegalArgumentException(statusName + " 상태는 processingLease가 비어 있어야 합니다.");
        }
    }

    private static void validateProcessedTimeAbsent(BoxEventTime processedTime, String statusName) {
        if (processedTime.isPresent()) {
            throw new IllegalArgumentException(statusName + " 상태는 processedTime이 비어 있어야 합니다.");
        }
    }

    private static void validateFailedStateAbsent(
            BoxEventTime failedTime,
            BoxFailureSnapshot<SlackInteractionFailureType> failure,
            String statusName
    ) {
        if (failedTime.isPresent()) {
            throw new IllegalArgumentException(statusName + " 상태는 failedTime이 비어 있어야 합니다.");
        }
        if (failure.isPresent()) {
            throw new IllegalArgumentException(statusName + " 상태는 failure가 비어 있어야 합니다.");
        }
    }

    private static void validateFailedStatePresent(
            BoxEventTime failedTime,
            BoxFailureSnapshot<SlackInteractionFailureType> failure,
            String statusName
    ) {
        if (!failedTime.isPresent()) {
            throw new IllegalArgumentException(statusName + " 상태는 failedTime이 있어야 합니다.");
        }
        if (!failure.isPresent()) {
            throw new IllegalArgumentException(statusName + " 상태는 failure가 있어야 합니다.");
        }
        if (failure.type() == SlackInteractionFailureType.ABSENT
                || failure.type() == SlackInteractionFailureType.NONE) {
            throw new IllegalArgumentException(statusName + " 상태의 failureType이 올바르지 않습니다.");
        }
    }

    private void validateProcessedAt(Instant processedAt) {
        if (processedAt == null) {
            throw new IllegalArgumentException("processedAt은 비어 있을 수 없습니다.");
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
        if (failureType == null
                || failureType == SlackInteractionFailureType.ABSENT
                || failureType == SlackInteractionFailureType.NONE) {
            throw new IllegalArgumentException("failureType은 ABSENT일 수 없습니다.");
        }
    }

    private void validateRetryPendingFailureType(SlackInteractionFailureType failureType) {
        validateFailureType(failureType);
        if (failureType == SlackInteractionFailureType.RETRYABLE
                || failureType == SlackInteractionFailureType.PROCESSING_TIMEOUT) {
            return;
        }

        throw new IllegalArgumentException("RETRY_PENDING failureType이 올바르지 않습니다.");
    }

    private void validateFailedFailureType(SlackInteractionFailureType failureType) {
        validateFailureType(failureType);
        if (failureType == SlackInteractionFailureType.BUSINESS_INVARIANT
                || failureType == SlackInteractionFailureType.RETRY_EXHAUSTED) {
            return;
        }

        throw new IllegalArgumentException("FAILED failureType이 올바르지 않습니다.");
    }

    private void validateClaimableStatus() {
        if (this.status == SlackInteractionInboxStatus.PENDING || this.status == SlackInteractionInboxStatus.RETRY_PENDING) {
            return;
        }

        throw new IllegalStateException("PROCESSING 전이는 PENDING 또는 RETRY_PENDING 상태에서만 가능합니다. 현재: " + this.status);
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

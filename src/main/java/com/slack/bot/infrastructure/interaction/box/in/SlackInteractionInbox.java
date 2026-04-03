package com.slack.bot.infrastructure.interaction.box.in;

import com.slack.bot.domain.common.BaseTimeEntity;
import com.slack.bot.infrastructure.common.BoxEventTime;
import com.slack.bot.infrastructure.common.BoxEventTimeDetail;
import com.slack.bot.infrastructure.common.BoxEventTimeState;
import com.slack.bot.infrastructure.common.BoxFailureDetail;
import com.slack.bot.infrastructure.common.BoxFailureSnapshot;
import com.slack.bot.infrastructure.common.BoxFailureState;
import com.slack.bot.infrastructure.common.BoxProcessingLease;
import com.slack.bot.infrastructure.common.BoxProcessingLeaseDetail;
import com.slack.bot.infrastructure.common.BoxProcessingLeaseState;
import com.slack.bot.infrastructure.interaction.box.SlackInteractionFailureType;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
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

    @Enumerated(EnumType.STRING)
    private BoxProcessingLeaseState processingLeaseState;

    @Getter(AccessLevel.NONE)
    private List<BoxProcessingLeaseDetail> processingLeaseDetails = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    private BoxEventTimeState processedTimeState;

    @Getter(AccessLevel.NONE)
    private List<BoxEventTimeDetail> processedTimeDetails = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    private BoxEventTimeState failedTimeState;

    @Getter(AccessLevel.NONE)
    private List<BoxEventTimeDetail> failedTimeDetails = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    private BoxFailureState failureState;

    @Getter(AccessLevel.NONE)
    private List<BoxFailureDetail> failureDetails = new ArrayList<>();

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
        clearProcessingLease();
        clearCompletion();
        clearFailure();
    }

    public SlackInteractionInboxHistory markProcessed(Instant processedAt) {
        validateProcessedAt(processedAt);
        validateTransition(SlackInteractionInboxStatus.PROCESSING, "PROCESSED");

        this.status = SlackInteractionInboxStatus.PROCESSED;
        clearProcessingLease();
        this.processedTimeState = BoxEventTimeState.PRESENT;
        replaceProcessedTime(processedAt);
        this.failedTimeState = BoxEventTimeState.ABSENT;
        clearFailedTime();
        clearFailure();

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

        this.processingLeaseState = BoxProcessingLeaseState.CLAIMED;
        replaceProcessingLease(processingStartedAt);
    }

    public void claim(Instant processingStartedAt) {
        validateProcessingStartedAt(processingStartedAt);
        validateClaimableStatus();

        this.status = SlackInteractionInboxStatus.PROCESSING;
        this.processingAttempt++;
        this.processingLeaseState = BoxProcessingLeaseState.CLAIMED;
        replaceProcessingLease(processingStartedAt);
        this.processedTimeState = BoxEventTimeState.ABSENT;
        clearProcessedTime();
        this.failedTimeState = BoxEventTimeState.ABSENT;
        clearFailedTime();
        clearFailure();
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
        clearProcessingLease();
        this.processedTimeState = BoxEventTimeState.ABSENT;
        clearProcessedTime();
        this.failedTimeState = BoxEventTimeState.PRESENT;
        replaceFailedTime(failedAt);
        this.failureState = BoxFailureState.PRESENT;
        replaceFailure(failedAt, failureReason, failureType);

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
        validateFailureType(failureType);
        validateTransition(SlackInteractionInboxStatus.PROCESSING, "FAILED");

        this.status = SlackInteractionInboxStatus.FAILED;
        clearProcessingLease();
        this.processedTimeState = BoxEventTimeState.ABSENT;
        clearProcessedTime();
        this.failedTimeState = BoxEventTimeState.PRESENT;
        replaceFailedTime(failedAt);
        this.failureState = BoxFailureState.PRESENT;
        replaceFailure(failedAt, failureReason, failureType);

        return SlackInteractionInboxHistory.completed(
                getId(),
                this.processingAttempt,
                SlackInteractionInboxStatus.FAILED,
                failedAt,
                BoxFailureSnapshot.present(failureReason, failureType)
        );
    }

    public BoxProcessingLease getProcessingLease() {
        if (processingLeaseState == BoxProcessingLeaseState.IDLE) {
            return BoxProcessingLease.idle();
        }

        return BoxProcessingLease.claimed(requireProcessingLeaseDetail().getStartedAt());
    }

    public BoxEventTime getProcessedTime() {
        if (processedTimeState == BoxEventTimeState.ABSENT) {
            return BoxEventTime.absent();
        }

        return BoxEventTime.present(requireProcessedTimeDetail().getOccurredAt());
    }

    public BoxEventTime getFailedTime() {
        if (failedTimeState == BoxEventTimeState.ABSENT) {
            return BoxEventTime.absent();
        }

        return BoxEventTime.present(requireFailedTimeDetail().getOccurredAt());
    }

    public BoxFailureSnapshot<SlackInteractionFailureType> getFailure() {
        if (failureState == BoxFailureState.ABSENT) {
            return BoxFailureSnapshot.absent();
        }

        BoxFailureDetail failureDetail = requireFailureDetail();

        return BoxFailureSnapshot.present(
                failureDetail.getFailureReason(),
                SlackInteractionFailureType.valueOf(failureDetail.getFailureTypeName())
        );
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

    private void clearProcessingLease() {
        this.processingLeaseState = BoxProcessingLeaseState.IDLE;
        this.processingLeaseDetails.clear();
    }

    private void clearCompletion() {
        this.processedTimeState = BoxEventTimeState.ABSENT;
        clearProcessedTime();
        this.failedTimeState = BoxEventTimeState.ABSENT;
        clearFailedTime();
    }

    private void clearFailure() {
        this.failureState = BoxFailureState.ABSENT;
        this.failureDetails.clear();
    }

    private void validateClaimableStatus() {
        if (this.status == SlackInteractionInboxStatus.PENDING || this.status == SlackInteractionInboxStatus.RETRY_PENDING) {
            return;
        }

        throw new IllegalStateException("PROCESSING 전이는 PENDING 또는 RETRY_PENDING 상태에서만 가능합니다. 현재: " + this.status);
    }

    private void replaceProcessingLease(Instant processingStartedAt) {
        if (this.processingLeaseDetails.isEmpty()) {
            this.processingLeaseDetails.add(BoxProcessingLeaseDetail.of(processingStartedAt));
            return;
        }

        requireProcessingLeaseDetail().updateStartedAt(processingStartedAt);
    }

    private void clearProcessedTime() {
        this.processedTimeDetails.clear();
    }

    private void replaceProcessedTime(Instant processedAt) {
        clearProcessedTime();
        this.processedTimeDetails.add(BoxEventTimeDetail.of(processedAt));
    }

    private void clearFailedTime() {
        this.failedTimeDetails.clear();
    }

    private void replaceFailedTime(Instant failedAt) {
        clearFailedTime();
        this.failedTimeDetails.add(BoxEventTimeDetail.of(failedAt));
    }

    private void replaceFailure(
            Instant failedAt,
            String failureReason,
            SlackInteractionFailureType failureType
    ) {
        this.failureDetails.clear();
        this.failureDetails.add(BoxFailureDetail.of(failedAt, failureReason, failureType));
    }

    private BoxProcessingLeaseDetail requireProcessingLeaseDetail() {
        validateSingleDetail(processingLeaseDetails, "processing lease");

        return processingLeaseDetails.getFirst();
    }

    private BoxEventTimeDetail requireProcessedTimeDetail() {
        validateSingleDetail(processedTimeDetails, "processed time");

        return processedTimeDetails.getFirst();
    }

    private BoxEventTimeDetail requireFailedTimeDetail() {
        validateSingleDetail(failedTimeDetails, "failed time");

        return failedTimeDetails.getFirst();
    }

    private BoxFailureDetail requireFailureDetail() {
        validateSingleDetail(failureDetails, "failure");

        return failureDetails.getFirst();
    }

    private void validateSingleDetail(List<?> details, String detailName) {
        if (details.size() == 1) {
            return;
        }

        throw new IllegalStateException(detailName + " detail 상태가 올바르지 않습니다.");
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

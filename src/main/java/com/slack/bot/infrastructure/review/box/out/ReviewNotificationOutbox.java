package com.slack.bot.infrastructure.review.box.out;

import com.slack.bot.infrastructure.common.BoxEventTime;
import com.slack.bot.infrastructure.common.BoxFailureSnapshot;
import com.slack.bot.infrastructure.common.BoxProcessingLease;
import com.slack.bot.infrastructure.interaction.box.SlackInteractionFailureType;
import java.time.Instant;
import lombok.Getter;

@Getter
public class ReviewNotificationOutbox {

    private final ReviewNotificationOutboxId identity;
    private final ReviewNotificationOutboxMessageType messageType;
    private final String idempotencyKey;
    private final ReviewNotificationOutboxProjectId projectId;
    private final String teamId;
    private final String channelId;
    private final ReviewNotificationOutboxStringField payloadJson;
    private final ReviewNotificationOutboxStringField blocksJson;
    private final ReviewNotificationOutboxStringField attachmentsJson;
    private final ReviewNotificationOutboxStringField fallbackText;

    private ReviewNotificationOutboxStatus status;
    private int processingAttempt;
    private BoxProcessingLease processingLease;
    private BoxEventTime sentTime;
    private BoxEventTime failedTime;
    private BoxFailureSnapshot<SlackInteractionFailureType> failure;

    public static ReviewNotificationOutbox semantic(
            String idempotencyKey,
            Long projectId,
            String teamId,
            String channelId,
            String payloadJson
    ) {
        validateIdempotencyKey(idempotencyKey);
        validateTeamId(teamId);
        validateChannelId(channelId);

        return new ReviewNotificationOutbox(
                ReviewNotificationOutboxId.unassigned(),
                ReviewNotificationOutboxMessageType.SEMANTIC,
                idempotencyKey,
                ReviewNotificationOutboxProjectId.present(projectId),
                teamId,
                channelId,
                ReviewNotificationOutboxStringField.present(payloadJson),
                ReviewNotificationOutboxStringField.absent(),
                ReviewNotificationOutboxStringField.absent(),
                ReviewNotificationOutboxStringField.absent(),
                ReviewNotificationOutboxStatus.PENDING,
                0,
                BoxProcessingLease.idle(),
                BoxEventTime.absent(),
                BoxEventTime.absent(),
                BoxFailureSnapshot.absent()
        );
    }

    public static ReviewNotificationOutbox channelBlocks(
            String idempotencyKey,
            String teamId,
            String channelId,
            String blocksJson,
            ReviewNotificationOutboxStringField attachmentsJson,
            ReviewNotificationOutboxStringField fallbackText
    ) {
        validateIdempotencyKey(idempotencyKey);
        validateTeamId(teamId);
        validateChannelId(channelId);

        ReviewNotificationOutboxStringField normalizedAttachmentsJson = normalizeField(attachmentsJson);
        ReviewNotificationOutboxStringField normalizedFallbackText = normalizeField(fallbackText);

        return new ReviewNotificationOutbox(
                ReviewNotificationOutboxId.unassigned(),
                ReviewNotificationOutboxMessageType.CHANNEL_BLOCKS,
                idempotencyKey,
                ReviewNotificationOutboxProjectId.absent(),
                teamId,
                channelId,
                ReviewNotificationOutboxStringField.absent(),
                ReviewNotificationOutboxStringField.present(blocksJson),
                normalizedAttachmentsJson,
                normalizedFallbackText,
                ReviewNotificationOutboxStatus.PENDING,
                0,
                BoxProcessingLease.idle(),
                BoxEventTime.absent(),
                BoxEventTime.absent(),
                BoxFailureSnapshot.absent()
        );
    }

    public static ReviewNotificationOutbox rehydrate(
            Long id,
            ReviewNotificationOutboxMessageType messageType,
            String idempotencyKey,
            ReviewNotificationOutboxProjectId projectId,
            String teamId,
            String channelId,
            ReviewNotificationOutboxStringField payloadJson,
            ReviewNotificationOutboxStringField blocksJson,
            ReviewNotificationOutboxStringField attachmentsJson,
            ReviewNotificationOutboxStringField fallbackText,
            ReviewNotificationOutboxStatus status,
            int processingAttempt,
            BoxProcessingLease processingLease,
            BoxEventTime sentTime,
            BoxEventTime failedTime,
            BoxFailureSnapshot<SlackInteractionFailureType> failure
    ) {
        validateIdempotencyKey(idempotencyKey);
        validateTeamId(teamId);
        validateChannelId(channelId);
        validateMessageType(messageType);
        validateStatus(status);
        validateProcessingAttempt(processingAttempt);
        validateProcessingLease(processingLease);
        validateSentTime(sentTime);
        validateFailedTime(failedTime);
        validateFailure(failure);
        validateMessageFields(
                messageType,
                normalizeProjectId(projectId),
                normalizeField(payloadJson),
                normalizeField(blocksJson),
                normalizeField(attachmentsJson),
                normalizeField(fallbackText)
        );
        validateState(status, processingAttempt, processingLease, sentTime, failedTime, failure);

        return new ReviewNotificationOutbox(
                ReviewNotificationOutboxId.assigned(id),
                messageType,
                idempotencyKey,
                normalizeProjectId(projectId),
                teamId,
                channelId,
                normalizeField(payloadJson),
                normalizeField(blocksJson),
                normalizeField(attachmentsJson),
                normalizeField(fallbackText),
                status,
                processingAttempt,
                processingLease,
                sentTime,
                failedTime,
                failure
        );
    }

    private ReviewNotificationOutbox(
            ReviewNotificationOutboxId identity,
            ReviewNotificationOutboxMessageType messageType,
            String idempotencyKey,
            ReviewNotificationOutboxProjectId projectId,
            String teamId,
            String channelId,
            ReviewNotificationOutboxStringField payloadJson,
            ReviewNotificationOutboxStringField blocksJson,
            ReviewNotificationOutboxStringField attachmentsJson,
            ReviewNotificationOutboxStringField fallbackText,
            ReviewNotificationOutboxStatus status,
            int processingAttempt,
            BoxProcessingLease processingLease,
            BoxEventTime sentTime,
            BoxEventTime failedTime,
            BoxFailureSnapshot<SlackInteractionFailureType> failure
    ) {
        this.identity = identity;
        this.messageType = messageType;
        this.idempotencyKey = idempotencyKey;
        this.projectId = projectId;
        this.teamId = teamId;
        this.channelId = channelId;
        this.payloadJson = payloadJson;
        this.blocksJson = blocksJson;
        this.attachmentsJson = attachmentsJson;
        this.fallbackText = fallbackText;
        this.status = status;
        this.processingAttempt = processingAttempt;
        this.processingLease = processingLease;
        this.sentTime = sentTime;
        this.failedTime = failedTime;
        this.failure = failure;
    }

    public boolean hasId() {
        return identity.isAssigned();
    }

    public boolean hasSemanticPayload() {
        return messageType == ReviewNotificationOutboxMessageType.SEMANTIC;
    }

    public boolean hasClaimedProcessingLease() {
        return processingLease.isClaimed();
    }

    public boolean hasClaimedProcessingLease(Instant processingStartedAt) {
        if (!processingLease.isClaimed()) {
            return false;
        }

        return processingLease.startedAt().equals(processingStartedAt);
    }

    public Instant currentProcessingLeaseStartedAt() {
        if (!processingLease.isClaimed()) {
            throw new IllegalStateException("processingLease를 보유하고 있지 않습니다.");
        }

        return processingLease.startedAt();
    }

    public long requiredProjectId() {
        return projectId.value();
    }

    public String requiredPayloadJson() {
        return payloadJson.value();
    }

    public String requiredBlocksJson() {
        return blocksJson.value();
    }

    public ReviewNotificationOutboxHistory markSent(Instant sentAt) {
        validateSentAt(sentAt);
        validateTransition(ReviewNotificationOutboxStatus.PROCESSING, "SENT");

        this.status = ReviewNotificationOutboxStatus.SENT;
        this.processingLease = BoxProcessingLease.idle();
        this.sentTime = BoxEventTime.present(sentAt);
        this.failedTime = BoxEventTime.absent();
        this.failure = BoxFailureSnapshot.absent();

        return ReviewNotificationOutboxHistory.completed(
                getId(),
                this.processingAttempt,
                ReviewNotificationOutboxStatus.SENT,
                sentAt,
                BoxFailureSnapshot.absent()
        );
    }

    public void renewProcessingLease(Instant processingStartedAt) {
        validateProcessingStartedAt(processingStartedAt);
        validateTransition(ReviewNotificationOutboxStatus.PROCESSING, "PROCESSING");

        this.processingLease = BoxProcessingLease.claimed(processingStartedAt);
    }

    public ReviewNotificationOutboxHistory markRetryPending(Instant failedAt, String failureReason) {
        return markRetryPending(failedAt, failureReason, SlackInteractionFailureType.RETRYABLE);
    }

    public ReviewNotificationOutboxHistory markRetryPending(
            Instant failedAt,
            String failureReason,
            SlackInteractionFailureType failureType
    ) {
        validateFailedAt(failedAt);
        validateFailureReason(failureReason);
        validateRetryPendingFailureType(failureType);
        validateTransition(ReviewNotificationOutboxStatus.PROCESSING, "RETRY_PENDING");

        this.status = ReviewNotificationOutboxStatus.RETRY_PENDING;
        this.processingLease = BoxProcessingLease.idle();
        this.sentTime = BoxEventTime.absent();
        this.failedTime = BoxEventTime.present(failedAt);
        this.failure = BoxFailureSnapshot.present(failureReason, failureType);

        return ReviewNotificationOutboxHistory.completed(
                getId(),
                this.processingAttempt,
                ReviewNotificationOutboxStatus.RETRY_PENDING,
                failedAt,
                BoxFailureSnapshot.present(failureReason, failureType)
        );
    }

    public ReviewNotificationOutboxHistory markFailed(
            Instant failedAt,
            String failureReason,
            SlackInteractionFailureType failureType
    ) {
        validateFailedAt(failedAt);
        validateFailureReason(failureReason);
        validateFailedFailureType(failureType);
        validateTransition(ReviewNotificationOutboxStatus.PROCESSING, "FAILED");

        this.status = ReviewNotificationOutboxStatus.FAILED;
        this.processingLease = BoxProcessingLease.idle();
        this.sentTime = BoxEventTime.absent();
        this.failedTime = BoxEventTime.present(failedAt);
        this.failure = BoxFailureSnapshot.present(failureReason, failureType);

        return ReviewNotificationOutboxHistory.completed(
                getId(),
                this.processingAttempt,
                ReviewNotificationOutboxStatus.FAILED,
                failedAt,
                BoxFailureSnapshot.present(failureReason, failureType)
        );
    }

    public Long getId() {
        return identity.value();
    }

    private static ReviewNotificationOutboxProjectId normalizeProjectId(ReviewNotificationOutboxProjectId projectId) {
        if (projectId == null) {
            return ReviewNotificationOutboxProjectId.absent();
        }

        return projectId;
    }

    private static ReviewNotificationOutboxStringField normalizeField(ReviewNotificationOutboxStringField field) {
        if (field == null) {
            return ReviewNotificationOutboxStringField.absent();
        }

        return field;
    }

    private static void validateMessageType(ReviewNotificationOutboxMessageType messageType) {
        if (messageType == null) {
            throw new IllegalArgumentException("messageType은 비어 있을 수 없습니다.");
        }
    }

    private static void validateMessageFields(
            ReviewNotificationOutboxMessageType messageType,
            ReviewNotificationOutboxProjectId projectId,
            ReviewNotificationOutboxStringField payloadJson,
            ReviewNotificationOutboxStringField blocksJson,
            ReviewNotificationOutboxStringField attachmentsJson,
            ReviewNotificationOutboxStringField fallbackText
    ) {
        if (messageType == ReviewNotificationOutboxMessageType.SEMANTIC) {
            validateSemanticFields(projectId, payloadJson, blocksJson, attachmentsJson, fallbackText);
            return;
        }

        validateChannelBlocksFields(projectId, payloadJson, blocksJson);
    }

    private static void validateSemanticFields(
            ReviewNotificationOutboxProjectId projectId,
            ReviewNotificationOutboxStringField payloadJson,
            ReviewNotificationOutboxStringField blocksJson,
            ReviewNotificationOutboxStringField attachmentsJson,
            ReviewNotificationOutboxStringField fallbackText
    ) {
        if (!projectId.isPresent()) {
            throw new IllegalArgumentException("semantic outbox에는 projectId가 필요합니다.");
        }
        if (!payloadJson.isPresent()) {
            throw new IllegalArgumentException("semantic outbox에는 payloadJson이 필요합니다.");
        }
        if (blocksJson.isPresent() || attachmentsJson.isPresent() || fallbackText.isPresent()) {
            throw new IllegalArgumentException("semantic outbox에는 snapshot 필드를 담을 수 없습니다.");
        }
    }

    private static void validateChannelBlocksFields(
            ReviewNotificationOutboxProjectId projectId,
            ReviewNotificationOutboxStringField payloadJson,
            ReviewNotificationOutboxStringField blocksJson
    ) {
        if (projectId.isPresent()) {
            throw new IllegalArgumentException("channel blocks outbox에는 projectId를 담을 수 없습니다.");
        }
        if (payloadJson.isPresent()) {
            throw new IllegalArgumentException("channel blocks outbox에는 payloadJson을 담을 수 없습니다.");
        }
        if (!blocksJson.isPresent()) {
            throw new IllegalArgumentException("channel blocks outbox에는 blocksJson이 필요합니다.");
        }
    }

    private static void validateStatus(ReviewNotificationOutboxStatus status) {
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

    private static void validateSentTime(BoxEventTime sentTime) {
        if (sentTime == null) {
            throw new IllegalArgumentException("sentTime은 비어 있을 수 없습니다.");
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
            ReviewNotificationOutboxStatus status,
            int processingAttempt,
            BoxProcessingLease processingLease,
            BoxEventTime sentTime,
            BoxEventTime failedTime,
            BoxFailureSnapshot<SlackInteractionFailureType> failure
    ) {
        if (status == ReviewNotificationOutboxStatus.PENDING) {
            validateInitialState("PENDING", processingAttempt, processingLease, sentTime, failedTime, failure);
            return;
        }
        if (status == ReviewNotificationOutboxStatus.PROCESSING) {
            validateProcessingState(processingAttempt, processingLease, sentTime, failedTime, failure);
            return;
        }
        if (status == ReviewNotificationOutboxStatus.SENT) {
            validateSentState(processingAttempt, processingLease, sentTime, failedTime, failure);
            return;
        }
        if (status == ReviewNotificationOutboxStatus.RETRY_PENDING) {
            validateRetryPendingState(processingAttempt, processingLease, sentTime, failedTime, failure);
            return;
        }
        if (status == ReviewNotificationOutboxStatus.FAILED) {
            validateFailedState(processingAttempt, processingLease, sentTime, failedTime, failure);
        }
    }

    private static void validateInitialState(
            String statusName,
            int processingAttempt,
            BoxProcessingLease processingLease,
            BoxEventTime sentTime,
            BoxEventTime failedTime,
            BoxFailureSnapshot<SlackInteractionFailureType> failure
    ) {
        if (processingAttempt != 0) {
            throw new IllegalArgumentException(statusName + " 상태의 processingAttempt는 0이어야 합니다.");
        }
        if (processingLease.isClaimed()) {
            throw new IllegalArgumentException(statusName + " 상태의 processingLease는 idle이어야 합니다.");
        }
        if (sentTime.isPresent() || failedTime.isPresent() || failure.isPresent()) {
            throw new IllegalArgumentException(statusName + " 상태에는 완료 정보가 없어야 합니다.");
        }
    }

    private static void validateProcessingState(
            int processingAttempt,
            BoxProcessingLease processingLease,
            BoxEventTime sentTime,
            BoxEventTime failedTime,
            BoxFailureSnapshot<SlackInteractionFailureType> failure
    ) {
        if (processingAttempt <= 0) {
            throw new IllegalArgumentException("PROCESSING 상태의 processingAttempt는 1 이상이어야 합니다.");
        }
        if (!processingLease.isClaimed()) {
            throw new IllegalArgumentException("PROCESSING 상태의 processingLease는 claimed여야 합니다.");
        }
        if (sentTime.isPresent() || failedTime.isPresent() || failure.isPresent()) {
            throw new IllegalArgumentException("PROCESSING 상태에는 완료 정보가 없어야 합니다.");
        }
    }

    private static void validateSentState(
            int processingAttempt,
            BoxProcessingLease processingLease,
            BoxEventTime sentTime,
            BoxEventTime failedTime,
            BoxFailureSnapshot<SlackInteractionFailureType> failure
    ) {
        if (processingAttempt <= 0) {
            throw new IllegalArgumentException("SENT 상태의 processingAttempt는 1 이상이어야 합니다.");
        }
        if (processingLease.isClaimed()) {
            throw new IllegalArgumentException("SENT 상태의 processingLease는 idle이어야 합니다.");
        }
        if (!sentTime.isPresent()) {
            throw new IllegalArgumentException("SENT 상태에는 sentTime이 필요합니다.");
        }
        if (failedTime.isPresent() || failure.isPresent()) {
            throw new IllegalArgumentException("SENT 상태에는 실패 정보가 없어야 합니다.");
        }
    }

    private static void validateRetryPendingState(
            int processingAttempt,
            BoxProcessingLease processingLease,
            BoxEventTime sentTime,
            BoxEventTime failedTime,
            BoxFailureSnapshot<SlackInteractionFailureType> failure
    ) {
        validateFailedCompletionState("RETRY_PENDING", processingAttempt, processingLease, sentTime, failedTime, failure);

        SlackInteractionFailureType failureType = failure.type();
        if (failureType.isRetryPendingOutboxFailureType()) {
            return;
        }

        throw new IllegalArgumentException("RETRY_PENDING 상태의 failureType이 올바르지 않습니다.");
    }

    private static void validateFailedState(
            int processingAttempt,
            BoxProcessingLease processingLease,
            BoxEventTime sentTime,
            BoxEventTime failedTime,
            BoxFailureSnapshot<SlackInteractionFailureType> failure
    ) {
        validateFailedCompletionState("FAILED", processingAttempt, processingLease, sentTime, failedTime, failure);

        SlackInteractionFailureType failureType = failure.type();
        if (failureType.isFailedOutboxFailureType()) {
            return;
        }

        throw new IllegalArgumentException("FAILED 상태의 failureType이 올바르지 않습니다.");
    }

    private static void validateFailedCompletionState(
            String statusName,
            int processingAttempt,
            BoxProcessingLease processingLease,
            BoxEventTime sentTime,
            BoxEventTime failedTime,
            BoxFailureSnapshot<SlackInteractionFailureType> failure
    ) {
        if (processingAttempt <= 0) {
            throw new IllegalArgumentException(statusName + " 상태의 processingAttempt는 1 이상이어야 합니다.");
        }
        if (processingLease.isClaimed()) {
            throw new IllegalArgumentException(statusName + " 상태의 processingLease는 idle이어야 합니다.");
        }
        if (sentTime.isPresent()) {
            throw new IllegalArgumentException(statusName + " 상태에는 sentTime이 없어야 합니다.");
        }
        if (!failedTime.isPresent() || !failure.isPresent()) {
            throw new IllegalArgumentException(statusName + " 상태에는 실패 정보가 필요합니다.");
        }
    }

    private void validateTransition(ReviewNotificationOutboxStatus expectedStatus, String targetStatus) {
        if (status == expectedStatus) {
            return;
        }

        throw new IllegalStateException(
                targetStatus + " 전이는 " + expectedStatus + " 상태에서만 가능합니다. 현재: " + status
        );
    }

    private static void validateIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("idempotencyKey는 비어 있을 수 없습니다.");
        }
    }

    private static void validateTeamId(String teamId) {
        if (teamId == null || teamId.isBlank()) {
            throw new IllegalArgumentException("teamId는 비어 있을 수 없습니다.");
        }
    }

    private static void validateChannelId(String channelId) {
        if (channelId == null || channelId.isBlank()) {
            throw new IllegalArgumentException("channelId는 비어 있을 수 없습니다.");
        }
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

    private void validateRetryPendingFailureType(SlackInteractionFailureType failureType) {
        if (failureType == null || !failureType.isRetryPendingOutboxFailureType()) {
            throw new IllegalArgumentException("RETRY_PENDING failureType이 올바르지 않습니다.");
        }
    }

    private void validateFailedFailureType(SlackInteractionFailureType failureType) {
        if (failureType == null || !failureType.isFailedOutboxFailureType()) {
            throw new IllegalArgumentException("FAILED failureType이 올바르지 않습니다.");
        }
    }
}

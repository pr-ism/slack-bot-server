package com.slack.bot.infrastructure.review.box.out;

import com.slack.bot.infrastructure.common.BoxFailureSnapshot;
import com.slack.bot.infrastructure.common.FailureSnapshotDefaults;
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
    private Instant processingStartedAt;
    private Instant sentAt;
    private Instant failedAt;
    private String failureReason;
    private SlackInteractionFailureType failureType;

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
                FailureSnapshotDefaults.NO_PROCESSING_STARTED_AT,
                FailureSnapshotDefaults.NO_SENT_AT,
                FailureSnapshotDefaults.NO_FAILURE_AT,
                FailureSnapshotDefaults.NO_FAILURE_REASON,
                SlackInteractionFailureType.NONE
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
                FailureSnapshotDefaults.NO_PROCESSING_STARTED_AT,
                FailureSnapshotDefaults.NO_SENT_AT,
                FailureSnapshotDefaults.NO_FAILURE_AT,
                FailureSnapshotDefaults.NO_FAILURE_REASON,
                SlackInteractionFailureType.NONE
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
            Instant processingStartedAt,
            Instant sentAt,
            Instant failedAt,
            String failureReason,
            SlackInteractionFailureType failureType
    ) {
        validateIdempotencyKey(idempotencyKey);
        validateTeamId(teamId);
        validateChannelId(channelId);
        validateMessageType(messageType);
        validateStatus(status);
        validateProcessingAttempt(processingAttempt);
        validateProcessingStartedAt(processingStartedAt);
        validateSentAtState(sentAt);
        validateFailedAtState(failedAt);
        validateFailureReasonState(failureReason);
        validateFailureTypeState(failureType);
        validateMessageFields(
                messageType,
                normalizeProjectId(projectId),
                normalizeField(payloadJson),
                normalizeField(blocksJson),
                normalizeField(attachmentsJson),
                normalizeField(fallbackText)
        );

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
                processingStartedAt,
                sentAt,
                failedAt,
                failureReason,
                failureType
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
            Instant processingStartedAt,
            Instant sentAt,
            Instant failedAt,
            String failureReason,
            SlackInteractionFailureType failureType
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
        this.processingStartedAt = processingStartedAt;
        this.sentAt = sentAt;
        this.failedAt = failedAt;
        this.failureReason = failureReason;
        this.failureType = failureType;
    }

    public boolean hasId() {
        return identity.isAssigned();
    }

    public boolean hasSemanticPayload() {
        return messageType == ReviewNotificationOutboxMessageType.SEMANTIC;
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
                BoxFailureSnapshot.absent()
        );
    }

    public void renewProcessingLease(Instant processingStartedAt) {
        validateProcessingStartedAt(processingStartedAt);
        validateTransition(ReviewNotificationOutboxStatus.PROCESSING, "PROCESSING");

        this.processingStartedAt = processingStartedAt;
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
        this.processingStartedAt = FailureSnapshotDefaults.NO_PROCESSING_STARTED_AT;
        this.sentAt = FailureSnapshotDefaults.NO_SENT_AT;
        this.failedAt = failedAt;
        this.failureReason = failureReason;
        this.failureType = failureType;

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

    private static void validateProcessingStartedAt(Instant processingStartedAt) {
        if (processingStartedAt == null) {
            throw new IllegalArgumentException("processingStartedAt은 비어 있을 수 없습니다.");
        }
    }

    private static void validateSentAtState(Instant sentAt) {
        if (sentAt == null) {
            throw new IllegalArgumentException("sentAt은 비어 있을 수 없습니다.");
        }
    }

    private static void validateFailedAtState(Instant failedAt) {
        if (failedAt == null) {
            throw new IllegalArgumentException("failedAt은 비어 있을 수 없습니다.");
        }
    }

    private static void validateFailureReasonState(String failureReason) {
        if (failureReason == null) {
            throw new IllegalArgumentException("failureReason은 비어 있을 수 없습니다.");
        }
    }

    private static void validateFailureTypeState(SlackInteractionFailureType failureType) {
        if (failureType == null) {
            throw new IllegalArgumentException("failureType은 비어 있을 수 없습니다.");
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

    private void validateRetryPendingFailureType(SlackInteractionFailureType failureType) {
        if (failureType == null
                || failureType == SlackInteractionFailureType.NONE
                || failureType == SlackInteractionFailureType.ABSENT
                || failureType == SlackInteractionFailureType.BUSINESS_INVARIANT
                || failureType == SlackInteractionFailureType.RETRY_EXHAUSTED) {
            throw new IllegalArgumentException("RETRY_PENDING failureType이 올바르지 않습니다.");
        }
    }
}

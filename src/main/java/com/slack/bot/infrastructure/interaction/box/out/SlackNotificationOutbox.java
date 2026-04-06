package com.slack.bot.infrastructure.interaction.box.out;

import com.slack.bot.infrastructure.common.BoxEventTime;
import com.slack.bot.infrastructure.common.BoxFailureSnapshot;
import com.slack.bot.infrastructure.common.BoxProcessingLease;
import com.slack.bot.infrastructure.interaction.box.SlackInteractionFailureType;
import java.time.Instant;
import lombok.Builder;
import lombok.Getter;

@Getter
public class SlackNotificationOutbox {

    private final SlackNotificationOutboxId identity;
    private final SlackNotificationOutboxMessageType messageType;
    private final String idempotencyKey;
    private final String teamId;
    private final String channelId;
    private final String userId;
    private final String text;
    private final String blocksJson;
    private final String fallbackText;

    private SlackNotificationOutboxStatus status;
    private int processingAttempt;
    private BoxProcessingLease processingLease;
    private BoxEventTime sentTime;
    private BoxEventTime failedTime;
    private BoxFailureSnapshot<SlackInteractionFailureType> failure;

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
        validateMessagePayload(messageType, userId, text, blocksJson, fallbackText);

        this.identity = SlackNotificationOutboxId.unassigned();
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
        this.processingLease = BoxProcessingLease.idle();
        this.sentTime = BoxEventTime.absent();
        this.failedTime = BoxEventTime.absent();
        this.failure = BoxFailureSnapshot.absent();
    }

    public static SlackNotificationOutbox rehydrate(
            Long id,
            SlackNotificationOutboxMessageType messageType,
            String idempotencyKey,
            String teamId,
            String channelId,
            String userId,
            String text,
            String blocksJson,
            String fallbackText,
            SlackNotificationOutboxStatus status,
            int processingAttempt,
            BoxProcessingLease processingLease,
            BoxEventTime sentTime,
            BoxEventTime failedTime,
            BoxFailureSnapshot<SlackInteractionFailureType> failure
    ) {
        validateMessageType(messageType);
        validateIdempotencyKey(idempotencyKey);
        validateTeamId(teamId);
        validateChannelId(channelId);
        validateMessagePayload(messageType, userId, text, blocksJson, fallbackText);
        validateStatus(status);
        validateProcessingAttempt(processingAttempt);
        validateProcessingLease(processingLease);
        validateSentTime(sentTime);
        validateFailedTime(failedTime);
        validateFailure(failure);
        validateState(status, processingAttempt, processingLease, sentTime, failedTime, failure);

        return new SlackNotificationOutbox(
                SlackNotificationOutboxId.assigned(id),
                messageType,
                idempotencyKey,
                teamId,
                channelId,
                userId,
                text,
                blocksJson,
                fallbackText,
                status,
                processingAttempt,
                processingLease,
                sentTime,
                failedTime,
                failure
        );
    }

    private SlackNotificationOutbox(
            SlackNotificationOutboxId identity,
            SlackNotificationOutboxMessageType messageType,
            String idempotencyKey,
            String teamId,
            String channelId,
            String userId,
            String text,
            String blocksJson,
            String fallbackText,
            SlackNotificationOutboxStatus status,
            int processingAttempt,
            BoxProcessingLease processingLease,
            BoxEventTime sentTime,
            BoxEventTime failedTime,
            BoxFailureSnapshot<SlackInteractionFailureType> failure
    ) {
        this.identity = identity;
        this.messageType = messageType;
        this.idempotencyKey = idempotencyKey;
        this.teamId = teamId;
        this.channelId = channelId;
        this.userId = userId;
        this.text = text;
        this.blocksJson = blocksJson;
        this.fallbackText = fallbackText;
        this.status = status;
        this.processingAttempt = processingAttempt;
        this.processingLease = processingLease;
        this.sentTime = sentTime;
        this.failedTime = failedTime;
        this.failure = failure;
    }

    public String requiredUserId() {
        return userId;
    }

    public Long getId() {
        return identity.value();
    }

    public boolean hasId() {
        return identity.isAssigned();
    }

    public String requiredText() {
        return text;
    }

    public String requiredBlocksJson() {
        return blocksJson;
    }

    public String fallbackTextOrBlank() {
        if (fallbackText == null) {
            return "";
        }

        return fallbackText;
    }

    public SlackNotificationOutboxHistory markSent(Instant sentAt) {
        validateSentAt(sentAt);
        validateTransition(SlackNotificationOutboxStatus.PROCESSING, "SENT");

        this.status = SlackNotificationOutboxStatus.SENT;
        this.processingLease = BoxProcessingLease.idle();
        this.sentTime = BoxEventTime.present(sentAt);
        this.failedTime = BoxEventTime.absent();
        this.failure = BoxFailureSnapshot.absent();

        return SlackNotificationOutboxHistory.completed(
                getId(),
                this.processingAttempt,
                SlackNotificationOutboxStatus.SENT,
                sentAt,
                BoxFailureSnapshot.absent()
        );
    }

    public void renewProcessingLease(Instant processingStartedAt) {
        validateProcessingStartedAt(processingStartedAt);
        validateTransition(SlackNotificationOutboxStatus.PROCESSING, "PROCESSING");

        this.processingLease = BoxProcessingLease.claimed(processingStartedAt);
    }

    public void claim(Instant processingStartedAt) {
        validateProcessingStartedAt(processingStartedAt);
        validateClaimableStatus();

        this.status = SlackNotificationOutboxStatus.PROCESSING;
        this.processingAttempt++;
        this.processingLease = BoxProcessingLease.claimed(processingStartedAt);
        this.sentTime = BoxEventTime.absent();
        this.failedTime = BoxEventTime.absent();
        this.failure = BoxFailureSnapshot.absent();
    }

    public SlackNotificationOutboxHistory markRetryPending(Instant failedAt, String failureReason) {
        return markRetryPending(failedAt, failureReason, SlackInteractionFailureType.RETRYABLE);
    }

    public SlackNotificationOutboxHistory markRetryPending(
            Instant failedAt,
            String failureReason,
            SlackInteractionFailureType failureType
    ) {
        validateTransition(SlackNotificationOutboxStatus.PROCESSING, "RETRY_PENDING");
        validateFailedAt(failedAt);
        validateFailureReason(failureReason);
        validateRetryPendingFailureType(failureType);

        this.status = SlackNotificationOutboxStatus.RETRY_PENDING;
        this.processingLease = BoxProcessingLease.idle();
        this.sentTime = BoxEventTime.absent();
        this.failedTime = BoxEventTime.present(failedAt);
        this.failure = BoxFailureSnapshot.present(failureReason, failureType);

        return SlackNotificationOutboxHistory.completed(
                getId(),
                this.processingAttempt,
                SlackNotificationOutboxStatus.RETRY_PENDING,
                failedAt,
                BoxFailureSnapshot.present(failureReason, failureType)
        );
    }

    public SlackNotificationOutboxHistory markFailed(
            Instant failedAt,
            String failureReason,
            SlackInteractionFailureType failureType
    ) {
        validateTransition(SlackNotificationOutboxStatus.PROCESSING, "FAILED");
        validateFailedAt(failedAt);
        validateFailureReason(failureReason);
        validateFailedFailureType(failureType);

        this.status = SlackNotificationOutboxStatus.FAILED;
        this.processingLease = BoxProcessingLease.idle();
        this.sentTime = BoxEventTime.absent();
        this.failedTime = BoxEventTime.present(failedAt);
        this.failure = BoxFailureSnapshot.present(failureReason, failureType);

        return SlackNotificationOutboxHistory.completed(
                getId(),
                this.processingAttempt,
                SlackNotificationOutboxStatus.FAILED,
                failedAt,
                BoxFailureSnapshot.present(failureReason, failureType)
        );
    }

    private static void validateMessageType(SlackNotificationOutboxMessageType messageType) {
        if (messageType == null) {
            throw new IllegalArgumentException("outbox messageType은 비어 있을 수 없습니다.");
        }
    }

    private static void validateIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("outbox idempotencyKey는 비어 있을 수 없습니다.");
        }
    }

    private static void validateTeamId(String teamId) {
        if (teamId == null || teamId.isBlank()) {
            throw new IllegalArgumentException("outbox teamId는 비어 있을 수 없습니다.");
        }
    }

    private static void validateChannelId(String channelId) {
        if (channelId == null || channelId.isBlank()) {
            throw new IllegalArgumentException("outbox channelId는 비어 있을 수 없습니다.");
        }
    }

    private static void validateMessagePayload(
            SlackNotificationOutboxMessageType messageType,
            String userId,
            String text,
            String blocksJson,
            String fallbackText
    ) {
        validateUserIdForEphemeral(messageType, userId);
        validateTextForTextType(messageType, text);
        validateBlocksJsonForBlocksType(messageType, blocksJson);
        validateNoUnexpectedUserId(messageType, userId);
        validateNoUnexpectedText(messageType, text);
        validateNoUnexpectedBlocksJson(messageType, blocksJson);
        validateNoUnexpectedFallbackText(messageType, fallbackText);
    }

    private static void validateStatus(SlackNotificationOutboxStatus status) {
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
            SlackNotificationOutboxStatus status,
            int processingAttempt,
            BoxProcessingLease processingLease,
            BoxEventTime sentTime,
            BoxEventTime failedTime,
            BoxFailureSnapshot<SlackInteractionFailureType> failure
    ) {
        if (status == SlackNotificationOutboxStatus.PENDING) {
            validatePendingState(processingAttempt, processingLease, sentTime, failedTime, failure);
            return;
        }
        if (status == SlackNotificationOutboxStatus.PROCESSING) {
            validateProcessingState(processingAttempt, processingLease, sentTime, failedTime, failure);
            return;
        }
        if (status == SlackNotificationOutboxStatus.SENT) {
            validateSentState(processingAttempt, processingLease, sentTime, failedTime, failure);
            return;
        }
        if (status == SlackNotificationOutboxStatus.RETRY_PENDING) {
            validateRetryPendingState(processingAttempt, processingLease, sentTime, failedTime, failure);
            return;
        }
        if (status == SlackNotificationOutboxStatus.FAILED) {
            validateFailedState(processingAttempt, processingLease, sentTime, failedTime, failure);
        }
    }

    private static void validatePendingState(
            int processingAttempt,
            BoxProcessingLease processingLease,
            BoxEventTime sentTime,
            BoxEventTime failedTime,
            BoxFailureSnapshot<SlackInteractionFailureType> failure
    ) {
        validateInitialState("PENDING", processingAttempt, processingLease, sentTime, failedTime, failure);
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
        if (failureType != SlackInteractionFailureType.RETRYABLE
                && failureType != SlackInteractionFailureType.PROCESSING_TIMEOUT) {
            throw new IllegalArgumentException("RETRY_PENDING 상태의 failureType이 올바르지 않습니다.");
        }
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
        if (failureType != SlackInteractionFailureType.BUSINESS_INVARIANT
                && failureType != SlackInteractionFailureType.RETRY_EXHAUSTED) {
            throw new IllegalArgumentException("FAILED 상태의 failureType이 올바르지 않습니다.");
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

    private static void validateUserIdForEphemeral(
            SlackNotificationOutboxMessageType messageType,
            String userId
    ) {
        if (messageType.supportsUserId() && isBlank(userId)) {
            throw new IllegalArgumentException("EPHEMERAL 메시지는 userId가 비어 있을 수 없습니다.");
        }
    }

    private static void validateTextForTextType(
            SlackNotificationOutboxMessageType messageType,
            String text
    ) {
        if (messageType.supportsText() && isBlank(text)) {
            throw new IllegalArgumentException("TEXT 타입 메시지는 text가 비어 있을 수 없습니다.");
        }
    }

    private static void validateBlocksJsonForBlocksType(
            SlackNotificationOutboxMessageType messageType,
            String blocksJson
    ) {
        if (messageType.supportsBlocksJson() && isBlank(blocksJson)) {
            throw new IllegalArgumentException("BLOCKS 타입 메시지는 blocksJson이 비어 있을 수 없습니다.");
        }
    }

    private static void validateNoUnexpectedUserId(
            SlackNotificationOutboxMessageType messageType,
            String userId
    ) {
        if (!messageType.supportsUserId() && userId != null) {
            throw new IllegalArgumentException("CHANNEL 메시지는 userId를 가질 수 없습니다.");
        }
    }

    private static void validateNoUnexpectedText(
            SlackNotificationOutboxMessageType messageType,
            String text
    ) {
        if (!messageType.supportsText() && text != null) {
            throw new IllegalArgumentException("BLOCKS 타입 메시지는 text를 가질 수 없습니다.");
        }
    }

    private static void validateNoUnexpectedBlocksJson(
            SlackNotificationOutboxMessageType messageType,
            String blocksJson
    ) {
        if (!messageType.supportsBlocksJson() && blocksJson != null) {
            throw new IllegalArgumentException("TEXT 타입 메시지는 blocksJson을 가질 수 없습니다.");
        }
    }

    private static void validateNoUnexpectedFallbackText(
            SlackNotificationOutboxMessageType messageType,
            String fallbackText
    ) {
        if (!messageType.supportsFallbackText() && fallbackText != null && !fallbackText.isBlank()) {
            throw new IllegalArgumentException("TEXT 타입 메시지는 fallbackText를 가질 수 없습니다.");
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
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

    private void validateFailureType(SlackInteractionFailureType failureType) {
        if (failureType == null
                || failureType == SlackInteractionFailureType.ABSENT
                || failureType == SlackInteractionFailureType.NONE) {
            throw new IllegalArgumentException("failureType은 ABSENT일 수 없습니다.");
        }
    }

    private void validateTransition(SlackNotificationOutboxStatus expected, String targetStatus) {
        if (this.status == expected) {
            return;
        }

        throw new IllegalStateException(
                targetStatus + " 전이는 " + expected + " 상태에서만 가능합니다. 현재: " + this.status
        );
    }

    private void validateClaimableStatus() {
        if (this.status == SlackNotificationOutboxStatus.PENDING || this.status == SlackNotificationOutboxStatus.RETRY_PENDING) {
            return;
        }

        throw new IllegalStateException(
                "PROCESSING 전이는 PENDING 또는 RETRY_PENDING 상태에서만 가능합니다. 현재: " + this.status
        );
    }

}

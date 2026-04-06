package com.slack.bot.infrastructure.interaction.box.out;

import com.slack.bot.infrastructure.common.BoxFailureSnapshot;
import com.slack.bot.infrastructure.interaction.box.SlackInteractionFailureType;
import java.time.Instant;
import lombok.Getter;

@Getter
public class SlackNotificationOutboxHistory {

    private final SlackNotificationOutboxHistoryId historyId;
    private final SlackNotificationOutboxId outboxId;
    private final int processingAttempt;
    private final SlackNotificationOutboxStatus status;
    private final Instant completedAt;
    private final BoxFailureSnapshot<SlackInteractionFailureType> failure;

    public static SlackNotificationOutboxHistory completed(
            Long outboxId,
            int processingAttempt,
            SlackNotificationOutboxStatus status,
            Instant completedAt,
            BoxFailureSnapshot<SlackInteractionFailureType> failure
    ) {
        validateOutboxId(outboxId);
        validateProcessingAttempt(processingAttempt);
        validateStatus(status);
        validateCompletedAt(completedAt);
        validateFailure(status, failure);

        return new SlackNotificationOutboxHistory(
                SlackNotificationOutboxHistoryId.unassigned(),
                SlackNotificationOutboxId.assigned(outboxId),
                processingAttempt,
                status,
                completedAt,
                failure
        );
    }

    public static SlackNotificationOutboxHistory rehydrate(
            Long id,
            Long outboxId,
            int processingAttempt,
            SlackNotificationOutboxStatus status,
            Instant completedAt,
            BoxFailureSnapshot<SlackInteractionFailureType> failure
    ) {
        validateOutboxId(outboxId);
        validateProcessingAttempt(processingAttempt);
        validateStatus(status);
        validateCompletedAt(completedAt);
        validateFailure(status, failure);

        return new SlackNotificationOutboxHistory(
                SlackNotificationOutboxHistoryId.assigned(id),
                SlackNotificationOutboxId.assigned(outboxId),
                processingAttempt,
                status,
                completedAt,
                failure
        );
    }

    private SlackNotificationOutboxHistory(
            SlackNotificationOutboxHistoryId historyId,
            SlackNotificationOutboxId outboxId,
            int processingAttempt,
            SlackNotificationOutboxStatus status,
            Instant completedAt,
            BoxFailureSnapshot<SlackInteractionFailureType> failure
    ) {
        this.historyId = historyId;
        this.outboxId = outboxId;
        this.processingAttempt = processingAttempt;
        this.status = status;
        this.completedAt = completedAt;
        this.failure = failure;
    }

    private static void validateOutboxId(Long outboxId) {
        if (outboxId == null || outboxId <= 0) {
            throw new IllegalArgumentException("outboxId는 비어 있을 수 없습니다.");
        }
    }

    public Long getId() {
        return historyId.value();
    }

    public Long getOutboxId() {
        return outboxId.value();
    }

    public boolean hasId() {
        return historyId.isAssigned();
    }

    private static void validateProcessingAttempt(int processingAttempt) {
        if (processingAttempt <= 0) {
            throw new IllegalArgumentException("processingAttempt는 1 이상이어야 합니다.");
        }
    }

    private static void validateStatus(SlackNotificationOutboxStatus status) {
        if (status == null) {
            throw new IllegalArgumentException("status는 비어 있을 수 없습니다.");
        }
        if (status == SlackNotificationOutboxStatus.PENDING || status == SlackNotificationOutboxStatus.PROCESSING) {
            throw new IllegalArgumentException("history status는 완료된 상태여야 합니다.");
        }
    }

    private static void validateCompletedAt(Instant completedAt) {
        if (completedAt == null) {
            throw new IllegalArgumentException("completedAt은 비어 있을 수 없습니다.");
        }
    }

    private static void validateFailure(
            SlackNotificationOutboxStatus status,
            BoxFailureSnapshot<SlackInteractionFailureType> failure
    ) {
        if (failure == null) {
            throw new IllegalArgumentException("failure는 비어 있을 수 없습니다.");
        }

        if (status == SlackNotificationOutboxStatus.SENT) {
            if (failure.isPresent()) {
                throw new IllegalArgumentException("SENT history에는 실패 정보가 없어야 합니다.");
            }
            return;
        }

        if (!failure.isPresent()) {
            throw new IllegalArgumentException("완료 실패 정보는 비어 있을 수 없습니다.");
        }

        SlackInteractionFailureType failureType = failure.type();
        if (failureType == SlackInteractionFailureType.ABSENT || failureType == SlackInteractionFailureType.NONE) {
            throw new IllegalArgumentException("완료 실패 정보의 failureType이 올바르지 않습니다.");
        }
        if (status == SlackNotificationOutboxStatus.RETRY_PENDING
                && failureType != SlackInteractionFailureType.RETRYABLE
                && failureType != SlackInteractionFailureType.PROCESSING_TIMEOUT) {
            throw new IllegalArgumentException("RETRY_PENDING history의 failureType이 올바르지 않습니다.");
        }
        if (status == SlackNotificationOutboxStatus.FAILED
                && failureType != SlackInteractionFailureType.BUSINESS_INVARIANT
                && failureType != SlackInteractionFailureType.RETRY_EXHAUSTED) {
            throw new IllegalArgumentException("FAILED history의 failureType이 올바르지 않습니다.");
        }
    }
}

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

    private static void validateProcessingAttempt(int processingAttempt) {
        if (processingAttempt <= 0) {
            throw new IllegalArgumentException("processingAttempt는 1 이상이어야 합니다.");
        }
    }

    private static void validateStatus(SlackNotificationOutboxStatus status) {
        if (status == null) {
            throw new IllegalArgumentException("status는 비어 있을 수 없습니다.");
        }
        status.validateHistoryStatus();
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
        status.validateHistoryFailure(failure);
    }
}

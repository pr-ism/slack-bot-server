package com.slack.bot.infrastructure.review.box.out;

import com.slack.bot.infrastructure.common.BoxFailureSnapshot;
import com.slack.bot.infrastructure.interaction.box.SlackInteractionFailureType;
import java.time.Instant;
import lombok.Getter;

@Getter
public class ReviewNotificationOutboxHistory {

    private final ReviewNotificationOutboxHistoryId historyId;
    private final ReviewNotificationOutboxId outboxId;
    private final int processingAttempt;
    private final ReviewNotificationOutboxStatus status;
    private final Instant completedAt;
    private final BoxFailureSnapshot<SlackInteractionFailureType> failure;

    public static ReviewNotificationOutboxHistory completed(
            Long outboxId,
            int processingAttempt,
            ReviewNotificationOutboxStatus status,
            Instant completedAt,
            BoxFailureSnapshot<SlackInteractionFailureType> failure
    ) {
        validateOutboxId(outboxId);
        validateProcessingAttempt(processingAttempt);
        validateStatus(status);
        validateCompletedAt(completedAt);
        validateFailure(status, failure);

        return new ReviewNotificationOutboxHistory(
                ReviewNotificationOutboxHistoryId.unassigned(),
                ReviewNotificationOutboxId.assigned(outboxId),
                processingAttempt,
                status,
                completedAt,
                failure
        );
    }

    public static ReviewNotificationOutboxHistory rehydrate(
            Long id,
            Long outboxId,
            int processingAttempt,
            ReviewNotificationOutboxStatus status,
            Instant completedAt,
            BoxFailureSnapshot<SlackInteractionFailureType> failure
    ) {
        validateOutboxId(outboxId);
        validateProcessingAttempt(processingAttempt);
        validateStatus(status);
        validateCompletedAt(completedAt);
        validateFailure(status, failure);

        return new ReviewNotificationOutboxHistory(
                ReviewNotificationOutboxHistoryId.assigned(id),
                ReviewNotificationOutboxId.assigned(outboxId),
                processingAttempt,
                status,
                completedAt,
                failure
        );
    }

    private ReviewNotificationOutboxHistory(
            ReviewNotificationOutboxHistoryId historyId,
            ReviewNotificationOutboxId outboxId,
            int processingAttempt,
            ReviewNotificationOutboxStatus status,
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

    public Long getId() {
        return historyId.value();
    }

    public Long getOutboxId() {
        return outboxId.value();
    }

    private static void validateOutboxId(Long outboxId) {
        if (outboxId == null || outboxId <= 0) {
            throw new IllegalArgumentException("outboxId는 비어 있을 수 없습니다.");
        }
    }

    private static void validateProcessingAttempt(int processingAttempt) {
        if (processingAttempt <= 0) {
            throw new IllegalArgumentException("processingAttempt는 1 이상이어야 합니다.");
        }
    }

    private static void validateStatus(ReviewNotificationOutboxStatus status) {
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
            ReviewNotificationOutboxStatus status,
            BoxFailureSnapshot<SlackInteractionFailureType> failure
    ) {
        status.validateHistoryFailure(failure);
    }
}

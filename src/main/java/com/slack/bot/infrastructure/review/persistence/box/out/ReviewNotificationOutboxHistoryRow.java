package com.slack.bot.infrastructure.review.persistence.box.out;

import com.slack.bot.infrastructure.common.BoxFailureSnapshot;
import com.slack.bot.infrastructure.interaction.box.SlackInteractionFailureType;
import com.slack.bot.infrastructure.review.box.out.ReviewNotificationOutboxHistory;
import com.slack.bot.infrastructure.review.box.out.ReviewNotificationOutboxStatus;
import java.time.Instant;
import lombok.Builder;
import lombok.Getter;

@Getter
public class ReviewNotificationOutboxHistoryRow {

    private Long id;
    private Long outboxId;
    private int processingAttempt;
    private ReviewNotificationOutboxStatus status;
    private Instant completedAt;
    private String failureReason;
    private SlackInteractionFailureType failureType;

    @Builder
    public ReviewNotificationOutboxHistoryRow(
            Long id,
            Long outboxId,
            int processingAttempt,
            ReviewNotificationOutboxStatus status,
            Instant completedAt,
            String failureReason,
            SlackInteractionFailureType failureType
    ) {
        this.id = id;
        this.outboxId = outboxId;
        this.processingAttempt = processingAttempt;
        this.status = status;
        this.completedAt = completedAt;
        this.failureReason = failureReason;
        this.failureType = failureType;
    }

    public static ReviewNotificationOutboxHistoryRow from(ReviewNotificationOutboxHistory history) {
        ReviewNotificationOutboxHistoryRowBuilder rowBuilder = ReviewNotificationOutboxHistoryRow.builder()
                                                                                                  .outboxId(history.getOutboxId())
                                                                                                  .processingAttempt(history.getProcessingAttempt())
                                                                                                  .status(history.getStatus())
                                                                                                  .completedAt(history.getCompletedAt())
                                                                                                  .failureReason(resolveFailureReason(history))
                                                                                                  .failureType(resolveFailureType(history));
        if (history.getHistoryId().isAssigned()) {
            rowBuilder.id(history.getId());
        }

        return rowBuilder.build();
    }

    public ReviewNotificationOutboxHistory toDomain() {
        try {
            return ReviewNotificationOutboxHistory.rehydrate(
                    id,
                    outboxId,
                    processingAttempt,
                    status,
                    completedAt,
                    toFailure()
            );
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("history 상태가 올바르지 않습니다.", exception);
        }
    }

    private static String resolveFailureReason(ReviewNotificationOutboxHistory history) {
        if (!history.getFailure().isPresent()) {
            return null;
        }

        return history.getFailure().reason();
    }

    private static SlackInteractionFailureType resolveFailureType(ReviewNotificationOutboxHistory history) {
        if (!history.getFailure().isPresent()) {
            return null;
        }

        return history.getFailure().type();
    }

    private BoxFailureSnapshot<SlackInteractionFailureType> toFailure() {
        if (failureReason == null && failureType == null) {
            return BoxFailureSnapshot.absent();
        }
        if (failureReason == null || failureType == null) {
            throw new IllegalStateException("history failure 상태가 올바르지 않습니다.");
        }

        return BoxFailureSnapshot.present(failureReason, failureType);
    }
}

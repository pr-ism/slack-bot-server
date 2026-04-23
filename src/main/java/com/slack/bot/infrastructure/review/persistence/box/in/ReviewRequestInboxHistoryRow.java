package com.slack.bot.infrastructure.review.persistence.box.in;

import com.slack.bot.infrastructure.common.BoxFailureSnapshot;
import com.slack.bot.infrastructure.review.box.in.ReviewRequestInboxFailureType;
import com.slack.bot.infrastructure.review.box.in.ReviewRequestInboxHistory;
import com.slack.bot.infrastructure.review.box.in.ReviewRequestInboxStatus;
import java.time.Instant;
import lombok.Getter;

@Getter
public class ReviewRequestInboxHistoryRow {

    private Long id;
    private final Long inboxId;
    private final int processingAttempt;
    private final ReviewRequestInboxStatus status;
    private final Instant completedAt;
    private final String failureReason;
    private final ReviewRequestInboxFailureType failureType;

    public ReviewRequestInboxHistoryRow(
            Long id,
            Long inboxId,
            int processingAttempt,
            ReviewRequestInboxStatus status,
            Instant completedAt,
            String failureReason,
            ReviewRequestInboxFailureType failureType
    ) {
        this.id = id;
        this.inboxId = inboxId;
        this.processingAttempt = processingAttempt;
        this.status = status;
        this.completedAt = completedAt;
        this.failureReason = failureReason;
        this.failureType = failureType;
    }

    public static ReviewRequestInboxHistoryRow from(ReviewRequestInboxHistory history) {
        FailureColumnValues failureColumnValues = FailureColumnValues.from(history.getFailure());
        return new ReviewRequestInboxHistoryRow(
                history.getId(),
                history.getInboxId(),
                history.getProcessingAttempt(),
                history.getStatus(),
                history.getCompletedAt(),
                failureColumnValues.reason(),
                failureColumnValues.type()
        );
    }

    public ReviewRequestInboxHistory toDomain() {
        try {
            return ReviewRequestInboxHistory.rehydrate(
                    id,
                    inboxId,
                    processingAttempt,
                    status,
                    completedAt,
                    toFailure()
            );
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("history 상태가 올바르지 않습니다.", e);
        }
    }

    private BoxFailureSnapshot<ReviewRequestInboxFailureType> toFailure() {
        if (failureReason == null && failureType == null) {
            return BoxFailureSnapshot.absent();
        }
        if (failureReason == null || failureType == null) {
            throw new IllegalStateException("history failure 상태가 올바르지 않습니다.");
        }

        return BoxFailureSnapshot.present(failureReason, failureType);
    }

    private record FailureColumnValues(
            String reason,
            ReviewRequestInboxFailureType type
    ) {

        private static FailureColumnValues from(BoxFailureSnapshot<ReviewRequestInboxFailureType> failure) {
            if (!failure.isPresent()) {
                return new FailureColumnValues(null, null);
            }

            return new FailureColumnValues(failure.reason(), failure.type());
        }
    }
}

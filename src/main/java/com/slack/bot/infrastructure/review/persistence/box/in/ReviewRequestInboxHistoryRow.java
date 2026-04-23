package com.slack.bot.infrastructure.review.persistence.box.in;

import com.slack.bot.infrastructure.common.BoxFailureSnapshot;
import com.slack.bot.infrastructure.review.box.in.ReviewRequestInboxFailureType;
import com.slack.bot.infrastructure.review.box.in.ReviewRequestInboxHistory;
import com.slack.bot.infrastructure.review.box.in.ReviewRequestInboxStatus;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ReviewRequestInboxHistoryRow {

    private Long id;
    private Long inboxId;
    private int processingAttempt;
    private ReviewRequestInboxStatus status;
    private Instant completedAt;
    private String failureReason;
    private ReviewRequestInboxFailureType failureType;

    public ReviewRequestInboxHistoryRow() {
    }

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
        ReviewRequestInboxHistoryRow row = new ReviewRequestInboxHistoryRow();
        row.setId(history.getId());
        row.setInboxId(history.getInboxId());
        row.setProcessingAttempt(history.getProcessingAttempt());
        row.setStatus(history.getStatus());
        row.setCompletedAt(history.getCompletedAt());
        row.applyFailure(history);
        return row;
    }

    public ReviewRequestInboxHistory toDomain() {
        return ReviewRequestInboxHistory.rehydrate(
                id,
                inboxId,
                processingAttempt,
                status,
                completedAt,
                toFailure()
        );
    }

    private void applyFailure(ReviewRequestInboxHistory history) {
        this.failureReason = null;
        this.failureType = null;

        BoxFailureSnapshot<ReviewRequestInboxFailureType> failure = history.getFailure();
        if (!failure.isPresent()) {
            return;
        }

        this.failureReason = failure.reason();
        this.failureType = failure.type();
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
}

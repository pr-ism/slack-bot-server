package com.slack.bot.infrastructure.review.persistence.box.in;

import com.slack.bot.domain.common.BaseTimeEntity;
import com.slack.bot.infrastructure.common.BoxFailureSnapshot;
import com.slack.bot.infrastructure.review.box.in.ReviewRequestInboxFailureType;
import com.slack.bot.infrastructure.review.box.in.ReviewRequestInboxHistory;
import com.slack.bot.infrastructure.review.box.in.ReviewRequestInboxStatus;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "review_request_inbox_history")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReviewRequestInboxHistoryJpaEntity extends BaseTimeEntity {

    private Long inboxId;

    private int processingAttempt;

    @Enumerated(EnumType.STRING)
    private ReviewRequestInboxStatus status;

    private Instant completedAt;

    private String failureReason;

    @Enumerated(EnumType.STRING)
    private ReviewRequestInboxFailureType failureType;

    public ReviewRequestInboxHistory toDomain() {
        return ReviewRequestInboxHistory.rehydrate(
                getId(),
                inboxId,
                processingAttempt,
                status,
                completedAt,
                toFailure()
        );
    }

    public void apply(ReviewRequestInboxHistory history) {
        this.inboxId = history.getInboxId();
        this.processingAttempt = history.getProcessingAttempt();
        this.status = history.getStatus();
        this.completedAt = history.getCompletedAt();
        applyFailure(history);
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

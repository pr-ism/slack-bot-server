package com.slack.bot.infrastructure.review.persistence.box.out;

import com.slack.bot.domain.common.BaseTimeEntity;
import com.slack.bot.infrastructure.common.BoxFailureSnapshot;
import com.slack.bot.infrastructure.interaction.box.SlackInteractionFailureType;
import com.slack.bot.infrastructure.review.box.out.ReviewNotificationOutboxHistory;
import com.slack.bot.infrastructure.review.box.out.ReviewNotificationOutboxStatus;
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
@Table(name = "review_notification_outbox_history")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReviewNotificationOutboxHistoryJpaEntity extends BaseTimeEntity {

    private Long outboxId;

    private int processingAttempt;

    @Enumerated(EnumType.STRING)
    private ReviewNotificationOutboxStatus status;

    private Instant completedAt;

    private String failureReason;

    @Enumerated(EnumType.STRING)
    private SlackInteractionFailureType failureType;

    public ReviewNotificationOutboxHistory toDomain() {
        return ReviewNotificationOutboxHistory.rehydrate(
                getId(),
                outboxId,
                processingAttempt,
                status,
                completedAt,
                toFailure()
        );
    }

    public void apply(ReviewNotificationOutboxHistory history) {
        this.outboxId = history.getOutboxId();
        this.processingAttempt = history.getProcessingAttempt();
        this.status = history.getStatus();
        this.completedAt = history.getCompletedAt();
        applyFailure(history);
    }

    private void applyFailure(ReviewNotificationOutboxHistory history) {
        this.failureReason = null;
        this.failureType = null;

        BoxFailureSnapshot<SlackInteractionFailureType> failure = history.getFailure();
        if (!failure.isPresent()) {
            return;
        }

        this.failureReason = failure.reason();
        this.failureType = failure.type();
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

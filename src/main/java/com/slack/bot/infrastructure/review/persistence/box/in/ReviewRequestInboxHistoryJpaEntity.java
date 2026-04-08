package com.slack.bot.infrastructure.review.persistence.box.in;

import com.slack.bot.domain.common.BaseTimeEntity;
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
                failureReason,
                failureType
        );
    }

    public void apply(ReviewRequestInboxHistory history) {
        this.inboxId = history.getInboxId();
        this.processingAttempt = history.getProcessingAttempt();
        this.status = history.getStatus();
        this.completedAt = history.getCompletedAt();
        this.failureReason = history.getFailureReason();
        this.failureType = history.getFailureType();
    }
}

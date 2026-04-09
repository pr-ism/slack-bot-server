package com.slack.bot.infrastructure.review.box.in;

import com.slack.bot.infrastructure.common.BoxFailureSnapshot;
import java.time.Instant;
import lombok.Getter;

@Getter
public class ReviewRequestInboxHistory {

    private final Long id;
    private final Long inboxId;
    private final int processingAttempt;
    private final ReviewRequestInboxStatus status;
    private final Instant completedAt;
    private final BoxFailureSnapshot<ReviewRequestInboxFailureType> failure;

    public static ReviewRequestInboxHistory completed(
            Long inboxId,
            int processingAttempt,
            ReviewRequestInboxStatus status,
            Instant completedAt,
            BoxFailureSnapshot<ReviewRequestInboxFailureType> failure
    ) {
        validateInboxIdIfPresent(inboxId);
        validateProcessingAttempt(processingAttempt);
        validateStatus(status);
        validateCompletedAt(completedAt);
        validateFailure(status, failure);

        return new ReviewRequestInboxHistory(
                null,
                inboxId,
                processingAttempt,
                status,
                completedAt,
                failure
        );
    }

    public static ReviewRequestInboxHistory rehydrate(
            Long id,
            Long inboxId,
            int processingAttempt,
            ReviewRequestInboxStatus status,
            Instant completedAt,
            BoxFailureSnapshot<ReviewRequestInboxFailureType> failure
    ) {
        validateInboxIdIfPresent(inboxId);
        validateProcessingAttempt(processingAttempt);
        validateStatus(status);
        validateCompletedAt(completedAt);
        validateFailure(status, failure);

        return new ReviewRequestInboxHistory(
                id,
                inboxId,
                processingAttempt,
                status,
                completedAt,
                failure
        );
    }

    private ReviewRequestInboxHistory(
            Long id,
            Long inboxId,
            int processingAttempt,
            ReviewRequestInboxStatus status,
            Instant completedAt,
            BoxFailureSnapshot<ReviewRequestInboxFailureType> failure
    ) {
        this.id = id;
        this.inboxId = inboxId;
        this.processingAttempt = processingAttempt;
        this.status = status;
        this.completedAt = completedAt;
        this.failure = failure;
    }

    public ReviewRequestInboxHistory bindInboxId(Long inboxId) {
        validateInboxId(inboxId);
        if (this.inboxId != null && !this.inboxId.equals(inboxId)) {
            throw new IllegalStateException("history inboxId를 다른 값으로 변경할 수 없습니다.");
        }
        if (this.inboxId != null) {
            return this;
        }

        return new ReviewRequestInboxHistory(
                this.id,
                inboxId,
                processingAttempt,
                status,
                completedAt,
                failure
        );
    }

    private static void validateInboxId(Long inboxId) {
        if (inboxId == null || inboxId <= 0) {
            throw new IllegalArgumentException("inboxId는 비어 있을 수 없습니다.");
        }
    }

    private static void validateInboxIdIfPresent(Long inboxId) {
        if (inboxId == null) {
            return;
        }

        validateInboxId(inboxId);
    }

    private static void validateProcessingAttempt(int processingAttempt) {
        if (processingAttempt <= 0) {
            throw new IllegalArgumentException("processingAttempt는 1 이상이어야 합니다.");
        }
    }

    private static void validateStatus(ReviewRequestInboxStatus status) {
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
            ReviewRequestInboxStatus status,
            BoxFailureSnapshot<ReviewRequestInboxFailureType> failure
    ) {
        status.validateHistoryFailure(failure);
    }
}

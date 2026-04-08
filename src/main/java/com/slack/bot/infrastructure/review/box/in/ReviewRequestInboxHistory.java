package com.slack.bot.infrastructure.review.box.in;

import com.slack.bot.infrastructure.common.FailureSnapshotDefaults;
import java.time.Instant;
import lombok.Getter;

@Getter
public class ReviewRequestInboxHistory {

    private final Long id;
    private final Long inboxId;
    private final int processingAttempt;
    private final ReviewRequestInboxStatus status;
    private final Instant completedAt;
    private final String failureReason;
    private final ReviewRequestInboxFailureType failureType;

    public static ReviewRequestInboxHistory completed(
            Long inboxId,
            int processingAttempt,
            ReviewRequestInboxStatus status,
            Instant completedAt,
            String failureReason,
            ReviewRequestInboxFailureType failureType
    ) {
        validateInboxIdIfPresent(inboxId);
        validateProcessingAttempt(processingAttempt);
        validateStatus(status);
        validateCompletedAt(completedAt);
        validateFailure(status, failureReason, failureType);

        return new ReviewRequestInboxHistory(
                null,
                inboxId,
                processingAttempt,
                status,
                completedAt,
                failureReason,
                failureType
        );
    }

    public static ReviewRequestInboxHistory rehydrate(
            Long id,
            Long inboxId,
            int processingAttempt,
            ReviewRequestInboxStatus status,
            Instant completedAt,
            String failureReason,
            ReviewRequestInboxFailureType failureType
    ) {
        validateInboxIdIfPresent(inboxId);
        validateProcessingAttempt(processingAttempt);
        validateStatus(status);
        validateCompletedAt(completedAt);
        validateFailure(status, failureReason, failureType);

        return new ReviewRequestInboxHistory(
                id,
                inboxId,
                processingAttempt,
                status,
                completedAt,
                failureReason,
                failureType
        );
    }

    private ReviewRequestInboxHistory(
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
                failureReason,
                failureType
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
        if (status == ReviewRequestInboxStatus.PENDING || status == ReviewRequestInboxStatus.PROCESSING) {
            throw new IllegalArgumentException("history status는 완료된 상태여야 합니다.");
        }
    }

    private static void validateCompletedAt(Instant completedAt) {
        if (completedAt == null) {
            throw new IllegalArgumentException("completedAt은 비어 있을 수 없습니다.");
        }
    }

    private static void validateFailure(
            ReviewRequestInboxStatus status,
            String failureReason,
            ReviewRequestInboxFailureType failureType
    ) {
        if (status == ReviewRequestInboxStatus.PROCESSED) {
            if (!FailureSnapshotDefaults.NO_FAILURE_REASON.equals(failureReason)
                    || failureType != ReviewRequestInboxFailureType.NONE) {
                throw new IllegalArgumentException("PROCESSED history에는 실패 정보가 없어야 합니다.");
            }
            return;
        }

        if (failureReason == null || failureReason.isBlank()) {
            throw new IllegalArgumentException("failureReason은 비어 있을 수 없습니다.");
        }
        if (status == ReviewRequestInboxStatus.FAILED) {
            if (failureType == null || failureType == ReviewRequestInboxFailureType.NONE) {
                throw new IllegalArgumentException("FAILED history에는 failureType이 필요합니다.");
            }
        }
        if (status == ReviewRequestInboxStatus.RETRY_PENDING) {
            if (failureType == null) {
                throw new IllegalArgumentException("RETRY_PENDING history에는 failureType이 필요합니다.");
            }
            if (failureType != ReviewRequestInboxFailureType.NONE
                    && failureType != ReviewRequestInboxFailureType.PROCESSING_TIMEOUT) {
                throw new IllegalArgumentException("RETRY_PENDING history의 failureType이 올바르지 않습니다.");
            }
        }
    }
}

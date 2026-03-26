package com.slack.bot.infrastructure.review.box.in;

import com.slack.bot.domain.common.BaseTimeEntity;
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
public class ReviewRequestInboxHistory extends BaseTimeEntity {

    private Long inboxId;

    private int processingAttempt;

    @Enumerated(EnumType.STRING)
    private ReviewRequestInboxStatus status;

    private Instant completedAt;

    private String failureReason;

    @Enumerated(EnumType.STRING)
    private ReviewRequestInboxFailureType failureType;

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
                inboxId,
                processingAttempt,
                status,
                completedAt,
                failureReason,
                failureType
        );
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
                inboxId,
                processingAttempt,
                status,
                completedAt,
                failureReason,
                failureType
        );
    }

    private ReviewRequestInboxHistory(
            Long inboxId,
            int processingAttempt,
            ReviewRequestInboxStatus status,
            Instant completedAt,
            String failureReason,
            ReviewRequestInboxFailureType failureType
    ) {
        this.inboxId = inboxId;
        this.processingAttempt = processingAttempt;
        this.status = status;
        this.completedAt = completedAt;
        this.failureReason = failureReason;
        this.failureType = failureType;
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
            if (failureReason != null || failureType != null) {
                throw new IllegalArgumentException("PROCESSED history에는 실패 정보가 없어야 합니다.");
            }
            return;
        }

        if (failureReason == null || failureReason.isBlank()) {
            throw new IllegalArgumentException("failureReason은 비어 있을 수 없습니다.");
        }
        if (status == ReviewRequestInboxStatus.FAILED && failureType == null) {
            throw new IllegalArgumentException("FAILED history에는 failureType이 필요합니다.");
        }
        if (status == ReviewRequestInboxStatus.RETRY_PENDING && failureType != null) {
            throw new IllegalArgumentException("RETRY_PENDING history에는 failureType이 없어야 합니다.");
        }
    }
}

package com.slack.bot.infrastructure.interaction.box.in;

import com.slack.bot.infrastructure.common.BoxFailureSnapshot;
import com.slack.bot.infrastructure.interaction.box.SlackInteractionFailureType;
import java.time.Instant;
import lombok.Getter;

@Getter
public class SlackInteractionInboxHistory {

    private final Long id;
    private final Long inboxId;
    private final int processingAttempt;
    private final SlackInteractionInboxStatus status;
    private final Instant completedAt;
    private final BoxFailureSnapshot<SlackInteractionFailureType> failure;

    public static SlackInteractionInboxHistory completed(
            Long inboxId,
            int processingAttempt,
            SlackInteractionInboxStatus status,
            Instant completedAt,
            BoxFailureSnapshot<SlackInteractionFailureType> failure
    ) {
        validateInboxIdIfPresent(inboxId);
        validateProcessingAttempt(processingAttempt);
        validateStatus(status);
        validateCompletedAt(completedAt);
        validateFailure(status, failure);

        return new SlackInteractionInboxHistory(
                null,
                inboxId,
                processingAttempt,
                status,
                completedAt,
                failure
        );
    }

    public static SlackInteractionInboxHistory rehydrate(
            Long id,
            Long inboxId,
            int processingAttempt,
            SlackInteractionInboxStatus status,
            Instant completedAt,
            BoxFailureSnapshot<SlackInteractionFailureType> failure
    ) {
        validateInboxIdIfPresent(inboxId);
        validateProcessingAttempt(processingAttempt);
        validateStatus(status);
        validateCompletedAt(completedAt);
        validateFailure(status, failure);

        return new SlackInteractionInboxHistory(
                id,
                inboxId,
                processingAttempt,
                status,
                completedAt,
                failure
        );
    }

    private SlackInteractionInboxHistory(
            Long id,
            Long inboxId,
            int processingAttempt,
            SlackInteractionInboxStatus status,
            Instant completedAt,
            BoxFailureSnapshot<SlackInteractionFailureType> failure
    ) {
        this.id = id;
        this.inboxId = inboxId;
        this.processingAttempt = processingAttempt;
        this.status = status;
        this.completedAt = completedAt;
        this.failure = failure;
    }

    public SlackInteractionInboxHistory bindInboxId(Long inboxId) {
        validateInboxId(inboxId);
        if (this.inboxId != null && !this.inboxId.equals(inboxId)) {
            throw new IllegalStateException("history inboxId를 다른 값으로 변경할 수 없습니다.");
        }
        if (this.inboxId != null) {
            return this;
        }

        return new SlackInteractionInboxHistory(
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

    private static void validateStatus(SlackInteractionInboxStatus status) {
        if (status == null) {
            throw new IllegalArgumentException("status는 비어 있을 수 없습니다.");
        }
        if (status == SlackInteractionInboxStatus.PENDING || status == SlackInteractionInboxStatus.PROCESSING) {
            throw new IllegalArgumentException("history status는 완료된 상태여야 합니다.");
        }
    }

    private static void validateCompletedAt(Instant completedAt) {
        if (completedAt == null) {
            throw new IllegalArgumentException("completedAt은 비어 있을 수 없습니다.");
        }
    }

    private static void validateFailure(
            SlackInteractionInboxStatus status,
            BoxFailureSnapshot<SlackInteractionFailureType> failure
    ) {
        if (failure == null) {
            throw new IllegalArgumentException("failure는 비어 있을 수 없습니다.");
        }

        if (status == SlackInteractionInboxStatus.PROCESSED) {
            if (failure.isPresent()) {
                throw new IllegalArgumentException("PROCESSED history에는 실패 정보가 없어야 합니다.");
            }
            return;
        }

        if (!failure.isPresent()) {
            throw new IllegalArgumentException("완료 실패 정보는 비어 있을 수 없습니다.");
        }

        SlackInteractionFailureType failureType = failure.type();
        if (failureType == SlackInteractionFailureType.ABSENT || failureType == SlackInteractionFailureType.NONE) {
            throw new IllegalArgumentException("완료 실패 정보의 failureType이 올바르지 않습니다.");
        }
        if (status == SlackInteractionInboxStatus.RETRY_PENDING
                && failureType != SlackInteractionFailureType.RETRYABLE
                && failureType != SlackInteractionFailureType.PROCESSING_TIMEOUT) {
            throw new IllegalArgumentException("RETRY_PENDING history의 failureType이 올바르지 않습니다.");
        }
        if (status == SlackInteractionInboxStatus.FAILED
                && failureType != SlackInteractionFailureType.BUSINESS_INVARIANT
                && failureType != SlackInteractionFailureType.RETRY_EXHAUSTED) {
            throw new IllegalArgumentException("FAILED history의 failureType이 올바르지 않습니다.");
        }
    }
}

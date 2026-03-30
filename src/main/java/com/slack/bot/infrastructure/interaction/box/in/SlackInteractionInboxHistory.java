package com.slack.bot.infrastructure.interaction.box.in;

import com.slack.bot.domain.common.BaseTimeEntity;
import com.slack.bot.infrastructure.common.FailureSnapshotDefaults;
import com.slack.bot.infrastructure.interaction.box.SlackInteractionFailureType;
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
@Table(name = "slack_interaction_inbox_history")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SlackInteractionInboxHistory extends BaseTimeEntity {

    private Long inboxId;

    private int processingAttempt;

    @Enumerated(EnumType.STRING)
    private SlackInteractionInboxStatus status;

    private Instant completedAt;

    private String failureReason;

    @Enumerated(EnumType.STRING)
    private SlackInteractionFailureType failureType;

    public static SlackInteractionInboxHistory completed(
            Long inboxId,
            int processingAttempt,
            SlackInteractionInboxStatus status,
            Instant completedAt,
            String failureReason,
            SlackInteractionFailureType failureType
    ) {
        validateInboxIdIfPresent(inboxId);
        validateProcessingAttempt(processingAttempt);
        validateStatus(status);
        validateCompletedAt(completedAt);
        validateFailure(status, failureReason, failureType);

        return new SlackInteractionInboxHistory(
                inboxId,
                processingAttempt,
                status,
                completedAt,
                failureReason,
                failureType
        );
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
                inboxId,
                processingAttempt,
                status,
                completedAt,
                failureReason,
                failureType
        );
    }

    private SlackInteractionInboxHistory(
            Long inboxId,
            int processingAttempt,
            SlackInteractionInboxStatus status,
            Instant completedAt,
            String failureReason,
            SlackInteractionFailureType failureType
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
            String failureReason,
            SlackInteractionFailureType failureType
    ) {
        if (status == SlackInteractionInboxStatus.PROCESSED) {
            if (!FailureSnapshotDefaults.NO_FAILURE_REASON.equals(failureReason)
                    || failureType != SlackInteractionFailureType.NONE) {
                throw new IllegalArgumentException("PROCESSED history에는 실패 정보가 없어야 합니다.");
            }
            return;
        }

        if (failureReason == null || failureReason.isBlank()) {
            throw new IllegalArgumentException("failureReason은 비어 있을 수 없습니다.");
        }
        if (status == SlackInteractionInboxStatus.FAILED && failureType == SlackInteractionFailureType.NONE) {
            throw new IllegalArgumentException("FAILED history에는 failureType이 필요합니다.");
        }
        if (status == SlackInteractionInboxStatus.RETRY_PENDING && failureType != SlackInteractionFailureType.NONE) {
            throw new IllegalArgumentException("RETRY_PENDING history에는 failureType이 없어야 합니다.");
        }
    }
}

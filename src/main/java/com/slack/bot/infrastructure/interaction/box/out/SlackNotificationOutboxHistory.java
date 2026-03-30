package com.slack.bot.infrastructure.interaction.box.out;

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
@Table(name = "slack_notification_outbox_history")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SlackNotificationOutboxHistory extends BaseTimeEntity {

    private Long outboxId;

    private int processingAttempt;

    @Enumerated(EnumType.STRING)
    private SlackNotificationOutboxStatus status;

    private Instant completedAt;

    private String failureReason;

    @Enumerated(EnumType.STRING)
    private SlackInteractionFailureType failureType;

    public static SlackNotificationOutboxHistory completed(
            Long outboxId,
            int processingAttempt,
            SlackNotificationOutboxStatus status,
            Instant completedAt,
            String failureReason,
            SlackInteractionFailureType failureType
    ) {
        validateOutboxIdIfPresent(outboxId);
        validateProcessingAttempt(processingAttempt);
        validateStatus(status);
        validateCompletedAt(completedAt);
        validateFailure(status, failureReason, failureType);

        return new SlackNotificationOutboxHistory(
                outboxId,
                processingAttempt,
                status,
                completedAt,
                failureReason,
                failureType
        );
    }

    public SlackNotificationOutboxHistory bindOutboxId(Long outboxId) {
        validateOutboxId(outboxId);
        if (this.outboxId != null && !this.outboxId.equals(outboxId)) {
            throw new IllegalStateException("history outboxId를 다른 값으로 변경할 수 없습니다.");
        }
        if (this.outboxId != null) {
            return this;
        }

        return new SlackNotificationOutboxHistory(
                outboxId,
                processingAttempt,
                status,
                completedAt,
                failureReason,
                failureType
        );
    }

    private SlackNotificationOutboxHistory(
            Long outboxId,
            int processingAttempt,
            SlackNotificationOutboxStatus status,
            Instant completedAt,
            String failureReason,
            SlackInteractionFailureType failureType
    ) {
        this.outboxId = outboxId;
        this.processingAttempt = processingAttempt;
        this.status = status;
        this.completedAt = completedAt;
        this.failureReason = failureReason;
        this.failureType = failureType;
    }

    private static void validateOutboxId(Long outboxId) {
        if (outboxId == null || outboxId <= 0) {
            throw new IllegalArgumentException("outboxId는 비어 있을 수 없습니다.");
        }
    }

    private static void validateOutboxIdIfPresent(Long outboxId) {
        if (outboxId == null) {
            return;
        }

        validateOutboxId(outboxId);
    }

    private static void validateProcessingAttempt(int processingAttempt) {
        if (processingAttempt <= 0) {
            throw new IllegalArgumentException("processingAttempt는 1 이상이어야 합니다.");
        }
    }

    private static void validateStatus(SlackNotificationOutboxStatus status) {
        if (status == null) {
            throw new IllegalArgumentException("status는 비어 있을 수 없습니다.");
        }
        if (status == SlackNotificationOutboxStatus.PENDING || status == SlackNotificationOutboxStatus.PROCESSING) {
            throw new IllegalArgumentException("history status는 완료된 상태여야 합니다.");
        }
    }

    private static void validateCompletedAt(Instant completedAt) {
        if (completedAt == null) {
            throw new IllegalArgumentException("completedAt은 비어 있을 수 없습니다.");
        }
    }

    private static void validateFailure(
            SlackNotificationOutboxStatus status,
            String failureReason,
            SlackInteractionFailureType failureType
    ) {
        if (status == SlackNotificationOutboxStatus.SENT) {
            if (!FailureSnapshotDefaults.NO_FAILURE_REASON.equals(failureReason)
                    || failureType != SlackInteractionFailureType.NONE) {
                throw new IllegalArgumentException("SENT history에는 실패 정보가 없어야 합니다.");
            }
            return;
        }

        if (failureReason == null || failureReason.isBlank()) {
            throw new IllegalArgumentException("failureReason은 비어 있을 수 없습니다.");
        }
        if (status == SlackNotificationOutboxStatus.FAILED) {
            if (failureType == null || failureType == SlackInteractionFailureType.NONE) {
                throw new IllegalArgumentException("FAILED history에는 failureType이 필요합니다.");
            }
        }
        if (status == SlackNotificationOutboxStatus.RETRY_PENDING && failureType != SlackInteractionFailureType.NONE) {
            throw new IllegalArgumentException("RETRY_PENDING history에는 failureType이 없어야 합니다.");
        }
    }
}

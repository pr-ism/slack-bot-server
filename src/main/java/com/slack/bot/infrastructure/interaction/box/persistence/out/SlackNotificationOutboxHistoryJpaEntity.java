package com.slack.bot.infrastructure.interaction.box.persistence.out;

import com.slack.bot.domain.common.BaseTimeEntity;
import com.slack.bot.infrastructure.common.BoxFailureSnapshot;
import com.slack.bot.infrastructure.common.BoxFailureState;
import com.slack.bot.infrastructure.common.FailureSnapshotDefaults;
import com.slack.bot.infrastructure.interaction.box.SlackInteractionFailureType;
import com.slack.bot.infrastructure.interaction.box.out.SlackNotificationOutboxHistory;
import com.slack.bot.infrastructure.interaction.box.out.SlackNotificationOutboxStatus;
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
public class SlackNotificationOutboxHistoryJpaEntity extends BaseTimeEntity {

    private Long outboxId;

    private int processingAttempt;

    @Enumerated(EnumType.STRING)
    private SlackNotificationOutboxStatus status;

    private Instant completedAt;

    @Enumerated(EnumType.STRING)
    private BoxFailureState failureState;

    private String failureReason;

    @Enumerated(EnumType.STRING)
    private SlackInteractionFailureType failureType;

    public SlackNotificationOutboxHistory toDomain() {
        return SlackNotificationOutboxHistory.rehydrate(
                getId(),
                outboxId,
                processingAttempt,
                status,
                completedAt,
                toFailure()
        );
    }

    public void apply(SlackNotificationOutboxHistory history) {
        this.outboxId = history.getOutboxId();
        this.processingAttempt = history.getProcessingAttempt();
        this.status = history.getStatus();
        this.completedAt = history.getCompletedAt();
        applyFailure(history);
    }

    private void applyFailure(SlackNotificationOutboxHistory history) {
        this.failureState = BoxFailureState.ABSENT;
        this.failureReason = FailureSnapshotDefaults.NO_FAILURE_REASON;
        this.failureType = SlackInteractionFailureType.NONE;

        BoxFailureSnapshot<SlackInteractionFailureType> failure = history.getFailure();
        if (!failure.isPresent()) {
            return;
        }

        this.failureState = BoxFailureState.PRESENT;
        this.failureReason = failure.reason();
        this.failureType = failure.type();
    }

    private BoxFailureSnapshot<SlackInteractionFailureType> toFailure() {
        validateFailureState();
        if (failureState == BoxFailureState.ABSENT) {
            return BoxFailureSnapshot.absent();
        }
        if (failureReason == null || failureType == null) {
            throw new IllegalStateException("history failure 상태가 올바르지 않습니다.");
        }

        return BoxFailureSnapshot.present(failureReason, failureType);
    }

    private void validateFailureState() {
        if (failureState == null) {
            throw new IllegalStateException("history failureState는 비어 있을 수 없습니다.");
        }
    }
}

package com.slack.bot.infrastructure.interaction.box.persistence.out;

import com.slack.bot.infrastructure.common.BoxFailureSnapshot;
import com.slack.bot.infrastructure.common.BoxFailureState;
import com.slack.bot.infrastructure.interaction.box.SlackInteractionFailureType;
import com.slack.bot.infrastructure.interaction.box.out.SlackNotificationOutboxHistory;
import com.slack.bot.infrastructure.interaction.box.out.SlackNotificationOutboxStatus;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SlackNotificationOutboxHistoryRow {

    private Long id;
    private Long outboxId;
    private int processingAttempt;
    private SlackNotificationOutboxStatus status;
    private Instant completedAt;
    private BoxFailureState failureState;
    private String failureReason;
    private SlackInteractionFailureType failureType;

    public static SlackNotificationOutboxHistoryRow from(SlackNotificationOutboxHistory history) {
        SlackNotificationOutboxHistoryRow row = new SlackNotificationOutboxHistoryRow();
        if (history.getHistoryId().isAssigned()) {
            row.setId(history.getId());
        }
        row.setOutboxId(history.getOutboxId());
        row.setProcessingAttempt(history.getProcessingAttempt());
        row.setStatus(history.getStatus());
        row.setCompletedAt(history.getCompletedAt());
        row.applyFailure(history);
        return row;
    }

    public SlackNotificationOutboxHistory toDomain() {
        return SlackNotificationOutboxHistory.rehydrate(
                id,
                outboxId,
                processingAttempt,
                status,
                completedAt,
                toFailure()
        );
    }

    private void applyFailure(SlackNotificationOutboxHistory history) {
        this.failureState = BoxFailureState.ABSENT;
        this.failureReason = null;
        this.failureType = null;

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

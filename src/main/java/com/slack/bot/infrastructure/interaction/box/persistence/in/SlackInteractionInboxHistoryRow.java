package com.slack.bot.infrastructure.interaction.box.persistence.in;

import com.slack.bot.infrastructure.common.BoxFailureSnapshot;
import com.slack.bot.infrastructure.interaction.box.SlackInteractionFailureType;
import com.slack.bot.infrastructure.interaction.box.in.SlackInteractionInboxHistory;
import com.slack.bot.infrastructure.interaction.box.in.SlackInteractionInboxStatus;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SlackInteractionInboxHistoryRow {

    private Long id;
    private Long inboxId;
    private int processingAttempt;
    private SlackInteractionInboxStatus status;
    private Instant completedAt;
    private String failureReason;
    private SlackInteractionFailureType failureType;

    public static SlackInteractionInboxHistoryRow from(SlackInteractionInboxHistory history) {
        SlackInteractionInboxHistoryRow row = new SlackInteractionInboxHistoryRow();
        row.setId(history.getId());
        row.setInboxId(history.getInboxId());
        row.setProcessingAttempt(history.getProcessingAttempt());
        row.setStatus(history.getStatus());
        row.setCompletedAt(history.getCompletedAt());
        row.applyFailure(history);
        return row;
    }

    public SlackInteractionInboxHistory toDomain() {
        return SlackInteractionInboxHistory.rehydrate(
                id,
                inboxId,
                processingAttempt,
                status,
                completedAt,
                toFailure()
        );
    }

    private void applyFailure(SlackInteractionInboxHistory history) {
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

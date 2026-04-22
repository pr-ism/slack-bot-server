package com.slack.bot.infrastructure.interaction.box.persistence.out;

import com.slack.bot.infrastructure.common.BoxFailureSnapshot;
import com.slack.bot.infrastructure.common.BoxFailureState;
import com.slack.bot.infrastructure.interaction.box.SlackInteractionFailureType;
import com.slack.bot.infrastructure.interaction.box.out.SlackNotificationOutboxHistory;
import com.slack.bot.infrastructure.interaction.box.out.SlackNotificationOutboxStatus;
import java.time.Instant;
import lombok.Builder;
import lombok.Getter;

@Getter
public class SlackNotificationOutboxHistoryRow {

    private Long id;
    private Long outboxId;
    private int processingAttempt;
    private SlackNotificationOutboxStatus status;
    private Instant completedAt;
    private BoxFailureState failureState;
    private String failureReason;
    private SlackInteractionFailureType failureType;

    @Builder
    public SlackNotificationOutboxHistoryRow(
            Long id,
            Long outboxId,
            int processingAttempt,
            SlackNotificationOutboxStatus status,
            Instant completedAt,
            BoxFailureState failureState,
            String failureReason,
            SlackInteractionFailureType failureType
    ) {
        this.id = id;
        this.outboxId = outboxId;
        this.processingAttempt = processingAttempt;
        this.status = status;
        this.completedAt = completedAt;
        this.failureState = failureState;
        this.failureReason = failureReason;
        this.failureType = failureType;
    }

    public static SlackNotificationOutboxHistoryRow from(SlackNotificationOutboxHistory history) {
        BoxFailureSnapshot<SlackInteractionFailureType> failure = history.getFailure();
        String failureReason = null;
        SlackInteractionFailureType failureType = null;
        if (failure.isPresent()) {
            failureReason = failure.reason();
            failureType = failure.type();
        }

        SlackNotificationOutboxHistoryRowBuilder rowBuilder = SlackNotificationOutboxHistoryRow.builder()
                                                                                                .outboxId(history.getOutboxId())
                                                                                                .processingAttempt(history.getProcessingAttempt())
                                                                                                .status(history.getStatus())
                                                                                                .completedAt(history.getCompletedAt())
                                                                                                .failureState(resolveFailureState(failure))
                                                                                                .failureReason(failureReason)
                                                                                                .failureType(failureType);
        if (history.getHistoryId().isAssigned()) {
            rowBuilder.id(history.getId());
        }

        return rowBuilder.build();
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

    private static BoxFailureState resolveFailureState(
            BoxFailureSnapshot<SlackInteractionFailureType> failure
    ) {
        if (failure.isPresent()) {
            return BoxFailureState.PRESENT;
        }

        return BoxFailureState.ABSENT;
    }
}

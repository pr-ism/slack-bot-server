package com.slack.bot.infrastructure.interaction.box.persistence.in;

import com.slack.bot.domain.common.BaseTimeEntity;
import com.slack.bot.infrastructure.common.BoxFailureSnapshot;
import com.slack.bot.infrastructure.interaction.box.SlackInteractionFailureType;
import com.slack.bot.infrastructure.interaction.box.in.SlackInteractionInboxHistory;
import com.slack.bot.infrastructure.interaction.box.in.SlackInteractionInboxStatus;
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
public class SlackInteractionInboxHistoryJpaEntity extends BaseTimeEntity {

    private Long inboxId;

    private int processingAttempt;

    @Enumerated(EnumType.STRING)
    private SlackInteractionInboxStatus status;

    private Instant completedAt;

    private String failureReason;

    @Enumerated(EnumType.STRING)
    private SlackInteractionFailureType failureType;

    public SlackInteractionInboxHistory toDomain() {
        return SlackInteractionInboxHistory.rehydrate(
                getId(),
                inboxId,
                processingAttempt,
                status,
                completedAt,
                toFailure()
        );
    }

    public void apply(SlackInteractionInboxHistory history) {
        this.inboxId = history.getInboxId();
        this.processingAttempt = history.getProcessingAttempt();
        this.status = history.getStatus();
        this.completedAt = history.getCompletedAt();
        this.failureReason = history.getFailure().isPresent()
                ? history.getFailure().reason()
                : null;
        this.failureType = history.getFailure().isPresent()
                ? history.getFailure().type()
                : null;
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

package com.slack.bot.infrastructure.interaction.box.persistence.in;

import com.slack.bot.domain.common.BaseTimeEntity;
import com.slack.bot.infrastructure.common.BoxEventTime;
import com.slack.bot.infrastructure.common.BoxFailureSnapshot;
import com.slack.bot.infrastructure.common.BoxProcessingLease;
import com.slack.bot.infrastructure.interaction.box.SlackInteractionFailureType;
import com.slack.bot.infrastructure.interaction.box.in.SlackInteractionInbox;
import com.slack.bot.infrastructure.interaction.box.in.SlackInteractionInboxStatus;
import com.slack.bot.infrastructure.interaction.box.in.SlackInteractionInboxType;
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
@Table(name = "slack_interaction_inbox")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SlackInteractionInboxJpaEntity extends BaseTimeEntity {

    @Enumerated(EnumType.STRING)
    private SlackInteractionInboxType interactionType;

    private String idempotencyKey;

    private String payloadJson;

    @Enumerated(EnumType.STRING)
    private SlackInteractionInboxStatus status;

    private int processingAttempt;

    private Instant processingStartedAt;

    private Instant processedAt;

    private Instant failedAt;

    private String failureReason;

    @Enumerated(EnumType.STRING)
    private SlackInteractionFailureType failureType;

    public SlackInteractionInbox toDomain() {
        return SlackInteractionInbox.rehydrate(
                getId(),
                interactionType,
                idempotencyKey,
                payloadJson,
                status,
                processingAttempt,
                toProcessingLease(),
                toProcessedTime(),
                toFailedTime(),
                toFailure()
        );
    }

    public void apply(SlackInteractionInbox inbox) {
        this.interactionType = inbox.getInteractionType();
        this.idempotencyKey = inbox.getIdempotencyKey();
        this.payloadJson = inbox.getPayloadJson();
        this.status = inbox.getStatus();
        this.processingAttempt = inbox.getProcessingAttempt();
        this.processingStartedAt = inbox.getProcessingLease().isClaimed()
                ? inbox.getProcessingLease().startedAt()
                : null;
        this.processedAt = inbox.getProcessedTime().isPresent()
                ? inbox.getProcessedTime().occurredAt()
                : null;
        this.failedAt = inbox.getFailedTime().isPresent()
                ? inbox.getFailedTime().occurredAt()
                : null;
        this.failureReason = inbox.getFailure().isPresent()
                ? inbox.getFailure().reason()
                : null;
        this.failureType = inbox.getFailure().isPresent()
                ? inbox.getFailure().type()
                : null;
    }

    private BoxProcessingLease toProcessingLease() {
        if (processingStartedAt == null) {
            return BoxProcessingLease.idle();
        }

        return BoxProcessingLease.claimed(processingStartedAt);
    }

    private BoxEventTime toProcessedTime() {
        if (processedAt == null) {
            return BoxEventTime.absent();
        }

        return BoxEventTime.present(processedAt);
    }

    private BoxEventTime toFailedTime() {
        if (failedAt == null) {
            return BoxEventTime.absent();
        }

        return BoxEventTime.present(failedAt);
    }

    private BoxFailureSnapshot<SlackInteractionFailureType> toFailure() {
        if (failureReason == null && failureType == null) {
            return BoxFailureSnapshot.absent();
        }
        if (failureReason == null || failureType == null) {
            throw new IllegalStateException("failure 상태가 올바르지 않습니다.");
        }

        return BoxFailureSnapshot.present(failureReason, failureType);
    }
}

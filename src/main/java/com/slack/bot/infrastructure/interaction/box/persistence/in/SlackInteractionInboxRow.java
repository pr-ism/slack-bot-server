package com.slack.bot.infrastructure.interaction.box.persistence.in;

import com.slack.bot.infrastructure.common.BoxEventTime;
import com.slack.bot.infrastructure.common.BoxFailureSnapshot;
import com.slack.bot.infrastructure.common.BoxProcessingLease;
import com.slack.bot.infrastructure.interaction.box.SlackInteractionFailureType;
import com.slack.bot.infrastructure.interaction.box.in.SlackInteractionInbox;
import com.slack.bot.infrastructure.interaction.box.in.SlackInteractionInboxStatus;
import com.slack.bot.infrastructure.interaction.box.in.SlackInteractionInboxType;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SlackInteractionInboxRow {

    private Long id;
    private SlackInteractionInboxType interactionType;
    private String idempotencyKey;
    private String payloadJson;
    private SlackInteractionInboxStatus status;
    private int processingAttempt;
    private Instant processingStartedAt;
    private Instant processedAt;
    private Instant failedAt;
    private String failureReason;
    private SlackInteractionFailureType failureType;

    public static SlackInteractionInboxRow from(SlackInteractionInbox inbox) {
        SlackInteractionInboxRow row = new SlackInteractionInboxRow();
        row.setId(inbox.getId());
        row.setInteractionType(inbox.getInteractionType());
        row.setIdempotencyKey(inbox.getIdempotencyKey());
        row.setPayloadJson(inbox.getPayloadJson());
        row.setStatus(inbox.getStatus());
        row.setProcessingAttempt(inbox.getProcessingAttempt());
        row.applyProcessingLease(inbox);
        row.applyProcessedTime(inbox);
        row.applyFailedTime(inbox);
        row.applyFailure(inbox);
        return row;
    }

    public SlackInteractionInbox toDomain() {
        return SlackInteractionInbox.rehydrate(
                id,
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

    private void applyProcessingLease(SlackInteractionInbox inbox) {
        this.processingStartedAt = null;
        if (inbox.getProcessingLease().isClaimed()) {
            this.processingStartedAt = inbox.getProcessingLease().startedAt();
        }
    }

    private void applyProcessedTime(SlackInteractionInbox inbox) {
        this.processedAt = null;
        if (inbox.getProcessedTime().isPresent()) {
            this.processedAt = inbox.getProcessedTime().occurredAt();
        }
    }

    private void applyFailedTime(SlackInteractionInbox inbox) {
        this.failedAt = null;
        if (inbox.getFailedTime().isPresent()) {
            this.failedAt = inbox.getFailedTime().occurredAt();
        }
    }

    private void applyFailure(SlackInteractionInbox inbox) {
        this.failureReason = null;
        this.failureType = null;

        BoxFailureSnapshot<SlackInteractionFailureType> failure = inbox.getFailure();
        if (!failure.isPresent()) {
            return;
        }

        this.failureReason = failure.reason();
        this.failureType = failure.type();
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

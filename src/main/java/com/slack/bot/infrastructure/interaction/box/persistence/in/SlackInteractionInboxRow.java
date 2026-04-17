package com.slack.bot.infrastructure.interaction.box.persistence.in;

import com.slack.bot.infrastructure.common.BoxEventTime;
import com.slack.bot.infrastructure.common.BoxFailureSnapshot;
import com.slack.bot.infrastructure.common.BoxProcessingLease;
import com.slack.bot.infrastructure.interaction.box.SlackInteractionFailureType;
import com.slack.bot.infrastructure.interaction.box.in.SlackInteractionInbox;
import com.slack.bot.infrastructure.interaction.box.in.SlackInteractionInboxStatus;
import com.slack.bot.infrastructure.interaction.box.in.SlackInteractionInboxType;
import java.time.Instant;
import lombok.Builder;
import lombok.Getter;

@Getter
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

    @Builder
    public SlackInteractionInboxRow(
            Long id,
            SlackInteractionInboxType interactionType,
            String idempotencyKey,
            String payloadJson,
            SlackInteractionInboxStatus status,
            int processingAttempt,
            Instant processingStartedAt,
            Instant processedAt,
            Instant failedAt,
            String failureReason,
            SlackInteractionFailureType failureType
    ) {
        this.id = id;
        this.interactionType = interactionType;
        this.idempotencyKey = idempotencyKey;
        this.payloadJson = payloadJson;
        this.status = status;
        this.processingAttempt = processingAttempt;
        this.processingStartedAt = processingStartedAt;
        this.processedAt = processedAt;
        this.failedAt = failedAt;
        this.failureReason = failureReason;
        this.failureType = failureType;
    }

    public static SlackInteractionInboxRow from(SlackInteractionInbox inbox) {
        Instant processingStartedAt = null;
        if (inbox.getProcessingLease().isClaimed()) {
            processingStartedAt = inbox.getProcessingLease().startedAt();
        }

        Instant processedAt = null;
        if (inbox.getProcessedTime().isPresent()) {
            processedAt = inbox.getProcessedTime().occurredAt();
        }

        Instant failedAt = null;
        if (inbox.getFailedTime().isPresent()) {
            failedAt = inbox.getFailedTime().occurredAt();
        }

        BoxFailureSnapshot<SlackInteractionFailureType> failure = inbox.getFailure();
        String failureReason = null;
        SlackInteractionFailureType failureType = null;
        if (failure.isPresent()) {
            failureReason = failure.reason();
            failureType = failure.type();
        }

        return SlackInteractionInboxRow.builder()
                                       .id(inbox.getId())
                                       .interactionType(inbox.getInteractionType())
                                       .idempotencyKey(inbox.getIdempotencyKey())
                                       .payloadJson(inbox.getPayloadJson())
                                       .status(inbox.getStatus())
                                       .processingAttempt(inbox.getProcessingAttempt())
                                       .processingStartedAt(processingStartedAt)
                                       .processedAt(processedAt)
                                       .failedAt(failedAt)
                                       .failureReason(failureReason)
                                       .failureType(failureType)
                                       .build();
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

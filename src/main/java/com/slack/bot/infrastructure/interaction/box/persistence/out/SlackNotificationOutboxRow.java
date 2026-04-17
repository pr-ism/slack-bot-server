package com.slack.bot.infrastructure.interaction.box.persistence.out;

import com.slack.bot.infrastructure.common.BoxEventTime;
import com.slack.bot.infrastructure.common.BoxEventTimeState;
import com.slack.bot.infrastructure.common.BoxFailureSnapshot;
import com.slack.bot.infrastructure.common.BoxFailureState;
import com.slack.bot.infrastructure.common.BoxProcessingLease;
import com.slack.bot.infrastructure.common.BoxProcessingLeaseState;
import com.slack.bot.infrastructure.interaction.box.SlackInteractionFailureType;
import com.slack.bot.infrastructure.interaction.box.out.SlackNotificationOutbox;
import com.slack.bot.infrastructure.interaction.box.out.SlackNotificationOutboxFieldState;
import com.slack.bot.infrastructure.interaction.box.out.SlackNotificationOutboxMessageType;
import com.slack.bot.infrastructure.interaction.box.out.SlackNotificationOutboxStatus;
import com.slack.bot.infrastructure.interaction.box.out.SlackNotificationOutboxStringField;
import java.time.Instant;
import lombok.Builder;
import lombok.Getter;

@Getter
public class SlackNotificationOutboxRow {

    private Long id;
    private SlackNotificationOutboxMessageType messageType;
    private String idempotencyKey;
    private String teamId;
    private String channelId;
    private SlackNotificationOutboxFieldState userIdState;
    private String userId;
    private SlackNotificationOutboxFieldState textState;
    private String text;
    private SlackNotificationOutboxFieldState blocksJsonState;
    private String blocksJson;
    private SlackNotificationOutboxFieldState fallbackTextState;
    private String fallbackText;
    private SlackNotificationOutboxStatus status;
    private int processingAttempt;
    private BoxProcessingLeaseState processingLeaseState;
    private Instant processingStartedAt;
    private BoxEventTimeState sentTimeState;
    private Instant sentAt;
    private BoxEventTimeState failedTimeState;
    private Instant failedAt;
    private BoxFailureState failureState;
    private String failureReason;
    private SlackInteractionFailureType failureType;

    @Builder
    public SlackNotificationOutboxRow(
            Long id,
            SlackNotificationOutboxMessageType messageType,
            String idempotencyKey,
            String teamId,
            String channelId,
            SlackNotificationOutboxFieldState userIdState,
            String userId,
            SlackNotificationOutboxFieldState textState,
            String text,
            SlackNotificationOutboxFieldState blocksJsonState,
            String blocksJson,
            SlackNotificationOutboxFieldState fallbackTextState,
            String fallbackText,
            SlackNotificationOutboxStatus status,
            int processingAttempt,
            BoxProcessingLeaseState processingLeaseState,
            Instant processingStartedAt,
            BoxEventTimeState sentTimeState,
            Instant sentAt,
            BoxEventTimeState failedTimeState,
            Instant failedAt,
            BoxFailureState failureState,
            String failureReason,
            SlackInteractionFailureType failureType
    ) {
        this.id = id;
        this.messageType = messageType;
        this.idempotencyKey = idempotencyKey;
        this.teamId = teamId;
        this.channelId = channelId;
        this.userIdState = userIdState;
        this.userId = userId;
        this.textState = textState;
        this.text = text;
        this.blocksJsonState = blocksJsonState;
        this.blocksJson = blocksJson;
        this.fallbackTextState = fallbackTextState;
        this.fallbackText = fallbackText;
        this.status = status;
        this.processingAttempt = processingAttempt;
        this.processingLeaseState = processingLeaseState;
        this.processingStartedAt = processingStartedAt;
        this.sentTimeState = sentTimeState;
        this.sentAt = sentAt;
        this.failedTimeState = failedTimeState;
        this.failedAt = failedAt;
        this.failureState = failureState;
        this.failureReason = failureReason;
        this.failureType = failureType;
    }

    public static SlackNotificationOutboxRow from(SlackNotificationOutbox outbox) {
        Instant processingStartedAt = null;
        if (outbox.getProcessingLease().isClaimed()) {
            processingStartedAt = outbox.getProcessingLease().startedAt();
        }

        Instant sentAt = null;
        if (outbox.getSentTime().isPresent()) {
            sentAt = outbox.getSentTime().occurredAt();
        }

        Instant failedAt = null;
        if (outbox.getFailedTime().isPresent()) {
            failedAt = outbox.getFailedTime().occurredAt();
        }

        BoxFailureSnapshot<SlackInteractionFailureType> failure = outbox.getFailure();
        String failureReason = null;
        SlackInteractionFailureType failureType = null;
        if (failure.isPresent()) {
            failureReason = failure.reason();
            failureType = failure.type();
        }

        return SlackNotificationOutboxRow.builder()
                                         .id(resolveId(outbox))
                                         .messageType(outbox.getMessageType())
                                         .idempotencyKey(outbox.getIdempotencyKey())
                                         .teamId(outbox.getTeamId())
                                         .channelId(outbox.getChannelId())
                                         .userIdState(outbox.getUserId().getState())
                                         .userId(outbox.getUserId().valueOrBlank())
                                         .textState(outbox.getText().getState())
                                         .text(outbox.getText().valueOrBlank())
                                         .blocksJsonState(outbox.getBlocksJson().getState())
                                         .blocksJson(outbox.getBlocksJson().valueOrBlank())
                                         .fallbackTextState(outbox.getFallbackText().getState())
                                         .fallbackText(outbox.getFallbackText().valueOrBlank())
                                         .status(outbox.getStatus())
                                         .processingAttempt(outbox.getProcessingAttempt())
                                         .processingLeaseState(resolveProcessingLeaseState(outbox))
                                         .processingStartedAt(processingStartedAt)
                                         .sentTimeState(resolveSentTimeState(outbox))
                                         .sentAt(sentAt)
                                         .failedTimeState(resolveFailedTimeState(outbox))
                                         .failedAt(failedAt)
                                         .failureState(resolveFailureState(failure))
                                         .failureReason(failureReason)
                                         .failureType(failureType)
                                         .build();
    }

    public SlackNotificationOutbox toDomain() {
        return SlackNotificationOutbox.rehydrate(
                id,
                messageType,
                idempotencyKey,
                teamId,
                channelId,
                toStringField(userIdState, userId),
                toStringField(textState, text),
                toStringField(blocksJsonState, blocksJson),
                toStringField(fallbackTextState, fallbackText),
                status,
                processingAttempt,
                toProcessingLease(),
                toSentTime(),
                toFailedTime(),
                toFailure()
        );
    }

    private static Long resolveId(SlackNotificationOutbox outbox) {
        if (!outbox.hasId()) {
            return null;
        }

        return outbox.getId();
    }

    private static BoxProcessingLeaseState resolveProcessingLeaseState(SlackNotificationOutbox outbox) {
        if (outbox.getProcessingLease().isClaimed()) {
            return BoxProcessingLeaseState.CLAIMED;
        }

        return BoxProcessingLeaseState.IDLE;
    }

    private static BoxEventTimeState resolveSentTimeState(SlackNotificationOutbox outbox) {
        if (outbox.getSentTime().isPresent()) {
            return BoxEventTimeState.PRESENT;
        }

        return BoxEventTimeState.ABSENT;
    }

    private static BoxEventTimeState resolveFailedTimeState(SlackNotificationOutbox outbox) {
        if (outbox.getFailedTime().isPresent()) {
            return BoxEventTimeState.PRESENT;
        }

        return BoxEventTimeState.ABSENT;
    }

    private static BoxFailureState resolveFailureState(
            BoxFailureSnapshot<SlackInteractionFailureType> failure
    ) {
        if (failure.isPresent()) {
            return BoxFailureState.PRESENT;
        }

        return BoxFailureState.ABSENT;
    }

    private SlackNotificationOutboxStringField toStringField(
            SlackNotificationOutboxFieldState state,
            String value
    ) {
        if (state == null) {
            throw new IllegalStateException("payload field state가 비어 있을 수 없습니다.");
        }
        if (value == null) {
            throw new IllegalStateException("payload field value가 비어 있을 수 없습니다.");
        }
        if (state == SlackNotificationOutboxFieldState.ABSENT) {
            return SlackNotificationOutboxStringField.absent();
        }

        return SlackNotificationOutboxStringField.present(value);
    }

    private BoxProcessingLease toProcessingLease() {
        validateProcessingLeaseState();
        if (processingLeaseState == BoxProcessingLeaseState.IDLE) {
            return BoxProcessingLease.idle();
        }
        if (processingStartedAt == null) {
            throw new IllegalStateException("processing lease detail이 비어 있을 수 없습니다.");
        }

        return BoxProcessingLease.claimed(processingStartedAt);
    }

    private BoxEventTime toSentTime() {
        validateSentTimeState();
        if (sentTimeState == BoxEventTimeState.ABSENT) {
            return BoxEventTime.absent();
        }
        if (sentAt == null) {
            throw new IllegalStateException("sent time detail이 비어 있을 수 없습니다.");
        }

        return BoxEventTime.present(sentAt);
    }

    private BoxEventTime toFailedTime() {
        validateFailedTimeState();
        if (failedTimeState == BoxEventTimeState.ABSENT) {
            return BoxEventTime.absent();
        }
        if (failedAt == null) {
            throw new IllegalStateException("failed time detail이 비어 있을 수 없습니다.");
        }

        return BoxEventTime.present(failedAt);
    }

    private BoxFailureSnapshot<SlackInteractionFailureType> toFailure() {
        validateFailureState();
        if (failureState == BoxFailureState.ABSENT) {
            return BoxFailureSnapshot.absent();
        }
        if (failureReason == null || failureType == null) {
            throw new IllegalStateException("failure 상태가 올바르지 않습니다.");
        }

        return BoxFailureSnapshot.present(failureReason, failureType);
    }

    private void validateProcessingLeaseState() {
        if (processingLeaseState == null) {
            throw new IllegalStateException("processingLeaseState는 비어 있을 수 없습니다.");
        }
    }

    private void validateSentTimeState() {
        if (sentTimeState == null) {
            throw new IllegalStateException("sentTimeState는 비어 있을 수 없습니다.");
        }
    }

    private void validateFailedTimeState() {
        if (failedTimeState == null) {
            throw new IllegalStateException("failedTimeState는 비어 있을 수 없습니다.");
        }
    }

    private void validateFailureState() {
        if (failureState == null) {
            throw new IllegalStateException("failureState는 비어 있을 수 없습니다.");
        }
    }
}

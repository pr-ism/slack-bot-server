package com.slack.bot.infrastructure.interaction.box.persistence.out;

import com.slack.bot.domain.common.BaseTimeEntity;
import com.slack.bot.infrastructure.common.BoxEventTime;
import com.slack.bot.infrastructure.common.BoxEventTimeState;
import com.slack.bot.infrastructure.common.BoxFailureSnapshot;
import com.slack.bot.infrastructure.common.BoxFailureState;
import com.slack.bot.infrastructure.common.BoxProcessingLease;
import com.slack.bot.infrastructure.common.BoxProcessingLeaseState;
import com.slack.bot.infrastructure.interaction.box.out.SlackNotificationOutboxFieldState;
import com.slack.bot.infrastructure.interaction.box.SlackInteractionFailureType;
import com.slack.bot.infrastructure.interaction.box.out.SlackNotificationOutbox;
import com.slack.bot.infrastructure.interaction.box.out.SlackNotificationOutboxMessageType;
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
@Table(name = "slack_notification_outbox")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SlackNotificationOutboxJpaEntity extends BaseTimeEntity {

    @Enumerated(EnumType.STRING)
    private SlackNotificationOutboxMessageType messageType;

    private String idempotencyKey;

    private String teamId;

    private String channelId;

    @Enumerated(EnumType.STRING)
    private SlackNotificationOutboxFieldState userIdState;

    private String userId;

    @Enumerated(EnumType.STRING)
    private SlackNotificationOutboxFieldState textState;

    private String text;

    @Enumerated(EnumType.STRING)
    private SlackNotificationOutboxFieldState blocksJsonState;

    private String blocksJson;

    @Enumerated(EnumType.STRING)
    private SlackNotificationOutboxFieldState fallbackTextState;

    private String fallbackText;

    @Enumerated(EnumType.STRING)
    private SlackNotificationOutboxStatus status;

    private int processingAttempt;

    @Enumerated(EnumType.STRING)
    private BoxProcessingLeaseState processingLeaseState;

    private Instant processingStartedAt;

    @Enumerated(EnumType.STRING)
    private BoxEventTimeState sentTimeState;

    private Instant sentAt;

    @Enumerated(EnumType.STRING)
    private BoxEventTimeState failedTimeState;

    private Instant failedAt;

    @Enumerated(EnumType.STRING)
    private BoxFailureState failureState;

    private String failureReason;

    @Enumerated(EnumType.STRING)
    private SlackInteractionFailureType failureType;

    public SlackNotificationOutbox toDomain() {
        return SlackNotificationOutbox.rehydrate(
                getId(),
                messageType,
                idempotencyKey,
                teamId,
                channelId,
                toFieldValue(userIdState, userId),
                toFieldValue(textState, text),
                toFieldValue(blocksJsonState, blocksJson),
                toFieldValue(fallbackTextState, fallbackText),
                status,
                processingAttempt,
                toProcessingLease(),
                toSentTime(),
                toFailedTime(),
                toFailure()
        );
    }

    public void apply(SlackNotificationOutbox outbox) {
        this.messageType = outbox.getMessageType();
        this.idempotencyKey = outbox.getIdempotencyKey();
        this.teamId = outbox.getTeamId();
        this.channelId = outbox.getChannelId();
        applyField(outbox.getUserId(), FieldType.USER_ID);
        applyField(outbox.getText(), FieldType.TEXT);
        applyField(outbox.getBlocksJson(), FieldType.BLOCKS_JSON);
        applyField(outbox.getFallbackText(), FieldType.FALLBACK_TEXT);
        this.status = outbox.getStatus();
        this.processingAttempt = outbox.getProcessingAttempt();
        applyProcessingLease(outbox);
        applySentTime(outbox);
        applyFailedTime(outbox);
        applyFailure(outbox);
    }

    private void applyProcessingLease(SlackNotificationOutbox outbox) {
        this.processingLeaseState = BoxProcessingLeaseState.IDLE;
        if (outbox.getProcessingLease().isClaimed()) {
            this.processingLeaseState = BoxProcessingLeaseState.CLAIMED;
            this.processingStartedAt = outbox.getProcessingLease().startedAt();
        }
    }

    private void applySentTime(SlackNotificationOutbox outbox) {
        this.sentTimeState = BoxEventTimeState.ABSENT;
        if (outbox.getSentTime().isPresent()) {
            this.sentTimeState = BoxEventTimeState.PRESENT;
            this.sentAt = outbox.getSentTime().occurredAt();
        }
    }

    private void applyFailedTime(SlackNotificationOutbox outbox) {
        this.failedTimeState = BoxEventTimeState.ABSENT;
        if (outbox.getFailedTime().isPresent()) {
            this.failedTimeState = BoxEventTimeState.PRESENT;
            this.failedAt = outbox.getFailedTime().occurredAt();
        }
    }

    private void applyFailure(SlackNotificationOutbox outbox) {
        this.failureState = BoxFailureState.ABSENT;

        BoxFailureSnapshot<SlackInteractionFailureType> failure = outbox.getFailure();
        if (!failure.isPresent()) {
            return;
        }

        this.failureState = BoxFailureState.PRESENT;
        this.failureReason = failure.reason();
        this.failureType = failure.type();
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

    private void applyField(String field, FieldType fieldType) {
        SlackNotificationOutboxFieldState state = resolveFieldState(field);
        String value = resolveFieldValue(field);
        if (fieldType == FieldType.USER_ID) {
            this.userIdState = state;
            this.userId = value;
            return;
        }
        if (fieldType == FieldType.TEXT) {
            this.textState = state;
            this.text = value;
            return;
        }
        if (fieldType == FieldType.BLOCKS_JSON) {
            this.blocksJsonState = state;
            this.blocksJson = value;
            return;
        }

        this.fallbackTextState = state;
        this.fallbackText = value;
    }

    private String toFieldValue(SlackNotificationOutboxFieldState state, String value) {
        if (state == null) {
            throw new IllegalStateException("payload field state가 비어 있을 수 없습니다.");
        }
        if (state == SlackNotificationOutboxFieldState.ABSENT) {
            return null;
        }
        if (value == null) {
            throw new IllegalStateException("payload field value가 비어 있을 수 없습니다.");
        }

        return value;
    }

    private SlackNotificationOutboxFieldState resolveFieldState(String value) {
        if (value == null) {
            return SlackNotificationOutboxFieldState.ABSENT;
        }

        return SlackNotificationOutboxFieldState.PRESENT;
    }

    private String resolveFieldValue(String value) {
        if (value == null) {
            return "";
        }

        return value;
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

    private enum FieldType {
        USER_ID,
        TEXT,
        BLOCKS_JSON,
        FALLBACK_TEXT
    }
}
